/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.db;

import com.dialtone.utils.LoggerUtil;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Initializes the SQLite database schema for the Dialtone web interface.
 *
 * Creates tables for users, screennames, and web sessions.
 * Handles schema versioning and migrations.
 */
public class SchemaInitializer {

    /**
     * Initializes the database schema if it doesn't exist.
     * Safe to call multiple times - only creates tables that don't exist.
     *
     * @param databaseManager Database connection manager
     * @throws SQLException if schema initialization fails
     */
    public static void initializeSchema(DatabaseManager databaseManager) throws SQLException {
        LoggerUtil.info("Initializing database schema...");

        try (Connection conn = databaseManager.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Enable foreign key constraints (SQLite requires this to be set per connection)
            stmt.execute("PRAGMA foreign_keys = ON");

            // Create users table (X OAuth accounts)
            createUsersTable(stmt);

            // Create screennames table (AOL screennames, max 3 per user)
            createScreennamesTable(stmt);

            // Create screenname_preferences table (per-screenname settings)
            createScreennamePreferencesTable(stmt);

            // Create web_sessions table (JWT session tracking)
            createWebSessionsTable(stmt);

            // Create admin-related tables
            createUserRolesTable(stmt);
            createAdminAuditLogTable(stmt);

            // Run migrations for existing databases BEFORE creating indexes
            migrateUsersTableForDiscord(stmt);
            migrateUsersTableForEmail(stmt);

            // Create magic link tokens table for email auth
            createMagicLinkTokensTable(stmt);

            // Create indexes for performance (after migrations complete)
            createIndexes(stmt);

            LoggerUtil.info("Database schema initialization completed successfully");
        }
    }

    /**
     * Checks if a column exists in a table using SQLite PRAGMA.
     * This is the safe way to check column existence without throwing exceptions.
     */
    private static boolean columnExists(Statement stmt, String tableName, String columnName) throws SQLException {
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
        try {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    return true;
                }
            }
            return false;
        } finally {
            rs.close();
        }
    }

    /**
     * Creates the users table for OAuth account information (X and Discord).
     */
    private static void createUsersTable(Statement stmt) throws SQLException {
        String createUsersTable = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                auth_provider TEXT NOT NULL DEFAULT 'x',
                x_user_id TEXT UNIQUE,
                x_username TEXT,
                x_display_name TEXT,
                discord_user_id TEXT UNIQUE,
                discord_username TEXT,
                discord_display_name TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                is_active BOOLEAN DEFAULT 1
            )
        """;

        stmt.execute(createUsersTable);
        LoggerUtil.debug("Created users table");
    }
    
    /**
     * Migrates existing users table to add Discord OAuth columns and make x_user_id nullable.
     * Safe to run multiple times - checks for each column's existence individually.
     * 
     * SQLite doesn't support ALTER COLUMN, so we need to recreate the table to remove NOT NULL.
     */
    private static void migrateUsersTableForDiscord(Statement stmt) throws SQLException {
        // Check if x_user_id has NOT NULL constraint by checking table schema
        boolean needsTableRecreation = isColumnNotNull(stmt, "users", "x_user_id");
        
        // Check each column individually - a partial migration may have occurred
        boolean hasAuthProvider = columnExists(stmt, "users", "auth_provider");
        boolean hasDiscordUserId = columnExists(stmt, "users", "discord_user_id");
        boolean hasDiscordUsername = columnExists(stmt, "users", "discord_username");
        boolean hasDiscordDisplayName = columnExists(stmt, "users", "discord_display_name");
        
        boolean allColumnsExist = hasAuthProvider && hasDiscordUserId && hasDiscordUsername && hasDiscordDisplayName;
        
        if (allColumnsExist && !needsTableRecreation) {
            LoggerUtil.debug("Discord migration already complete - all columns exist and x_user_id is nullable");
            return;
        }
        
        if (needsTableRecreation) {
            LoggerUtil.info("Recreating users table to make x_user_id nullable for Discord-only users...");
            migrateUsersTableWithRecreation(stmt);
            LoggerUtil.info("Users table recreation completed");
            return;
        }
        
        LoggerUtil.info("Migrating users table for Discord OAuth support...");
        
        // Add each missing column individually
        if (!hasAuthProvider) {
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN auth_provider TEXT NOT NULL DEFAULT 'x'");
                LoggerUtil.debug("Added auth_provider column");
            } catch (SQLException e) {
                LoggerUtil.debug("auth_provider column already exists or error: " + e.getMessage());
            }
        }
        
        if (!hasDiscordUserId) {
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN discord_user_id TEXT");
                LoggerUtil.debug("Added discord_user_id column");
            } catch (SQLException e) {
                LoggerUtil.debug("discord_user_id column already exists or error: " + e.getMessage());
            }
        }
        
        if (!hasDiscordUsername) {
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN discord_username TEXT");
                LoggerUtil.debug("Added discord_username column");
            } catch (SQLException e) {
                LoggerUtil.debug("discord_username column already exists or error: " + e.getMessage());
            }
        }
        
        if (!hasDiscordDisplayName) {
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN discord_display_name TEXT");
                LoggerUtil.debug("Added discord_display_name column");
            } catch (SQLException e) {
                LoggerUtil.debug("discord_display_name column already exists or error: " + e.getMessage());
            }
        }
        
        LoggerUtil.info("Users table migration for Discord completed");
    }
    
    /**
     * Checks if a column has NOT NULL constraint using SQLite PRAGMA.
     */
    private static boolean isColumnNotNull(Statement stmt, String tableName, String columnName) throws SQLException {
        ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")");
        try {
            while (rs.next()) {
                String name = rs.getString("name");
                if (columnName.equalsIgnoreCase(name)) {
                    // notnull column: 1 means NOT NULL, 0 means nullable
                    return rs.getInt("notnull") == 1;
                }
            }
            return false; // Column doesn't exist
        } finally {
            rs.close();
        }
    }
    
    /**
     * Recreates the users table to change x_user_id from NOT NULL to nullable.
     * This is the SQLite way to alter column constraints.
     */
    private static void migrateUsersTableWithRecreation(Statement stmt) throws SQLException {
        // CRITICAL: Disable foreign key checks during migration
        // Other tables (screennames, web_sessions, etc.) reference users table
        stmt.execute("PRAGMA foreign_keys = OFF");
        
        try {
            // Step 0: Clean up any leftover temp table from failed migration
            stmt.execute("DROP TABLE IF EXISTS users_new");
            
            // Step 1: Create new table with correct schema
            String createNewTable = """
                CREATE TABLE users_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    auth_provider TEXT NOT NULL DEFAULT 'x',
                    x_user_id TEXT,
                    x_username TEXT,
                    x_display_name TEXT,
                    discord_user_id TEXT,
                    discord_username TEXT,
                    discord_display_name TEXT,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    is_active BOOLEAN DEFAULT 1
                )
            """;
            stmt.execute(createNewTable);
            
            // Step 2: Copy data from old table
            // Check which columns exist in old table to build appropriate INSERT
            boolean hasAuthProvider = columnExists(stmt, "users", "auth_provider");
            boolean hasDiscordUserId = columnExists(stmt, "users", "discord_user_id");
            
            String copyData;
            if (hasAuthProvider && hasDiscordUserId) {
                // Full schema already exists
                copyData = """
                    INSERT INTO users_new (id, auth_provider, x_user_id, x_username, x_display_name,
                        discord_user_id, discord_username, discord_display_name, created_at, is_active)
                    SELECT id, auth_provider, x_user_id, x_username, x_display_name,
                        discord_user_id, discord_username, discord_display_name, created_at, is_active
                    FROM users
                """;
            } else if (hasAuthProvider) {
                // Has auth_provider but no discord columns
                copyData = """
                    INSERT INTO users_new (id, auth_provider, x_user_id, x_username, x_display_name, created_at, is_active)
                    SELECT id, auth_provider, x_user_id, x_username, x_display_name, created_at, is_active
                    FROM users
                """;
            } else {
                // Original schema - only X OAuth columns
                copyData = """
                    INSERT INTO users_new (id, auth_provider, x_user_id, x_username, x_display_name, created_at, is_active)
                    SELECT id, 'x', x_user_id, x_username, x_display_name, created_at, is_active
                    FROM users
                """;
            }
            stmt.execute(copyData);
            
            // Step 3: Drop old table
            stmt.execute("DROP TABLE users");
            
            // Step 4: Rename new table
            stmt.execute("ALTER TABLE users_new RENAME TO users");
            
            // Step 5: Recreate unique index on x_user_id (but allow nulls)
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_x_user_id ON users(x_user_id) WHERE x_user_id IS NOT NULL");
            
            LoggerUtil.info("Users table recreated with nullable x_user_id");
        } finally {
            // Re-enable foreign key checks
            stmt.execute("PRAGMA foreign_keys = ON");
        }
    }

    /**
     * Migrates existing users table to add email column for email authentication.
     * Safe to run multiple times - checks for column existence first.
     */
    private static void migrateUsersTableForEmail(Statement stmt) throws SQLException {
        if (!columnExists(stmt, "users", "email")) {
            LoggerUtil.info("Migrating users table for email authentication...");
            try {
                stmt.execute("ALTER TABLE users ADD COLUMN email TEXT");
                LoggerUtil.debug("Added email column to users table");
            } catch (SQLException e) {
                LoggerUtil.debug("email column already exists or error: " + e.getMessage());
            }
        } else {
            LoggerUtil.debug("Email migration already complete - email column exists");
        }
    }

    /**
     * Creates the magic_link_tokens table for email authentication.
     * Stores temporary tokens that users click to authenticate.
     */
    private static void createMagicLinkTokensTable(Statement stmt) throws SQLException {
        String createMagicLinkTokensTable = """
            CREATE TABLE IF NOT EXISTS magic_link_tokens (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                email TEXT NOT NULL,
                token TEXT UNIQUE NOT NULL,
                user_id INTEGER,
                expires_at TIMESTAMP NOT NULL,
                used_at TIMESTAMP,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                ip_address TEXT,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        stmt.execute(createMagicLinkTokensTable);
        LoggerUtil.debug("Created magic_link_tokens table");
    }

    /**
     * Creates the screennames table for AOL screennames linked to users.
     */
    private static void createScreennamesTable(Statement stmt) throws SQLException {
        String createScreennamesTable = """
            CREATE TABLE IF NOT EXISTS screennames (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                screenname TEXT UNIQUE NOT NULL,
                password_hash TEXT NOT NULL,
                is_primary BOOLEAN DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK(length(screenname) >= 1 AND length(screenname) <= 10)
            )
        """;

        stmt.execute(createScreennamesTable);
        LoggerUtil.debug("Created screennames table");
    }

    /**
     * Creates the screenname_preferences table for per-screenname settings.
     * One-to-one relationship with screennames table.
     * Preferences are optional - defaults are used if no row exists.
     */
    private static void createScreennamePreferencesTable(Statement stmt) throws SQLException {
        String createPreferencesTable = """
            CREATE TABLE IF NOT EXISTS screenname_preferences (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                screenname_id INTEGER NOT NULL UNIQUE,
                low_color_mode BOOLEAN DEFAULT 0,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (screenname_id) REFERENCES screennames(id) ON DELETE CASCADE
            )
        """;

        stmt.execute(createPreferencesTable);
        LoggerUtil.debug("Created screenname_preferences table");
    }

    /**
     * Creates the web_sessions table for JWT session management.
     */
    private static void createWebSessionsTable(Statement stmt) throws SQLException {
        String createWebSessionsTable = """
            CREATE TABLE IF NOT EXISTS web_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                session_token TEXT UNIQUE NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            )
        """;

        stmt.execute(createWebSessionsTable);
        LoggerUtil.debug("Created web_sessions table");
    }

    /**
     * Creates the user_roles table for admin role management.
     */
    private static void createUserRolesTable(Statement stmt) throws SQLException {
        String createUserRolesTable = """
            CREATE TABLE IF NOT EXISTS user_roles (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL,
                role TEXT NOT NULL DEFAULT 'user',
                granted_by INTEGER,
                granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                expires_at TIMESTAMP,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (granted_by) REFERENCES users(id),
                UNIQUE(user_id, role)
            )
        """;

        stmt.execute(createUserRolesTable);
        LoggerUtil.debug("Created user_roles table");
    }

    /**
     * Creates the admin_audit_log table for tracking admin actions.
     */
    private static void createAdminAuditLogTable(Statement stmt) throws SQLException {
        String createAuditLogTable = """
            CREATE TABLE IF NOT EXISTS admin_audit_log (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                admin_user_id INTEGER NOT NULL,
                action TEXT NOT NULL,
                target_user_id INTEGER,
                target_screenname_id INTEGER,
                details TEXT,
                ip_address TEXT,
                user_agent TEXT,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (admin_user_id) REFERENCES users(id),
                FOREIGN KEY (target_user_id) REFERENCES users(id),
                FOREIGN KEY (target_screenname_id) REFERENCES screennames(id)
            )
        """;

        stmt.execute(createAuditLogTable);
        LoggerUtil.debug("Created admin_audit_log table");
    }

    /**
     * Creates database indexes for performance optimization.
     * Discord-related indexes are only created if the columns exist.
     */
    private static void createIndexes(Statement stmt) throws SQLException {
        // Core indexes that always exist
        String[] coreIndexes = {
            "CREATE INDEX IF NOT EXISTS idx_screennames_user_id ON screennames(user_id)",
            "CREATE UNIQUE INDEX IF NOT EXISTS idx_screenname_lower ON screennames(LOWER(screenname))",
            "CREATE INDEX IF NOT EXISTS idx_screenname_preferences_screenname_id ON screenname_preferences(screenname_id)",
            "CREATE INDEX IF NOT EXISTS idx_web_sessions_token ON web_sessions(session_token)",
            "CREATE INDEX IF NOT EXISTS idx_web_sessions_user_id ON web_sessions(user_id)",
            "CREATE INDEX IF NOT EXISTS idx_web_sessions_expires ON web_sessions(expires_at)",
            "CREATE INDEX IF NOT EXISTS idx_user_roles_user_id ON user_roles(user_id)",
            "CREATE INDEX IF NOT EXISTS idx_user_roles_role ON user_roles(role)",
            "CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin ON admin_audit_log(admin_user_id)",
            "CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created ON admin_audit_log(created_at)",
            "CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action ON admin_audit_log(action)"
        };

        for (String indexSql : coreIndexes) {
            stmt.execute(indexSql);
        }

        // Discord-related indexes - only create if columns exist
        if (columnExists(stmt, "users", "discord_user_id")) {
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_auth_provider ON users(auth_provider)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_users_discord_user_id ON users(discord_user_id)");
            LoggerUtil.debug("Created Discord-related indexes");
        } else {
            LoggerUtil.debug("Skipping Discord indexes - columns not yet migrated");
        }

        // Email-related indexes - only create if column exists
        if (columnExists(stmt, "users", "email")) {
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email IS NOT NULL");
            LoggerUtil.debug("Created email index on users table");
        }

        // Magic link token indexes
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_magic_link_token ON magic_link_tokens(token)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_magic_link_expires ON magic_link_tokens(expires_at)");
        stmt.execute("CREATE INDEX IF NOT EXISTS idx_magic_link_email ON magic_link_tokens(email)");
        LoggerUtil.debug("Created magic link token indexes");

        LoggerUtil.debug("Created database indexes");
    }

    /**
     * Checks if the database schema is properly initialized.
     *
     * @param databaseManager Database connection manager
     * @return true if schema is initialized, false otherwise
     */
    public static boolean isSchemaInitialized(DatabaseManager databaseManager) {
        try (Connection conn = databaseManager.getDataSource().getConnection();
             Statement stmt = conn.createStatement()) {

            // Check if core tables exist
            var rs = stmt.executeQuery("""
                SELECT name FROM sqlite_master
                WHERE type='table' AND name IN ('users', 'screennames', 'screenname_preferences', 'web_sessions', 'user_roles', 'admin_audit_log')
            """);

            int tableCount = 0;
            while (rs.next()) {
                tableCount++;
            }

            return tableCount == 6;

        } catch (SQLException e) {
            LoggerUtil.warn("Failed to check schema initialization: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the current schema version.
     * For future use when we need to handle schema migrations.
     *
     * @param databaseManager Database connection manager
     * @return Schema version (currently always 1)
     */
    public static int getSchemaVersion(DatabaseManager databaseManager) {
        // For now, we're at schema version 1
        // In the future, this could read from a schema_version table
        return 1;
    }
}
