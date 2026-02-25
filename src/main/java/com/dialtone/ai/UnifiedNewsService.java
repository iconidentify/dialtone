/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.dialtone.utils.LoggerUtil;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unified news service that handles all news categories through data-driven configuration.
 * Replaces separate GrokNewsService, GrokCryptoNewsService, GrokSportsService, etc.
 *
 * Key features:
 * - Single service instance for all news categories
 * - Configuration-driven prompts and behavior
 * - Unified persistence layer
 * - Single scheduler and HTTP client for efficiency
 * - Daily quota tracking per category
 * - Background fetching with smart initial delays
 */
public class UnifiedNewsService implements AutoCloseable {

    private static final String DEFAULT_LOADING_MESSAGE = "Loading...";
    private static final int DEFAULT_REFRESH_INTERVAL_MINUTES = 1440; // 24 hours

    private final GrokClient grokClient;
    private final ScheduledExecutorService scheduler;
    private final UnifiedNewsPersistence persistence;
    private final Map<NewsCategory, NewsConfig> categoryConfigs;
    private final Map<NewsCategory, AtomicReference<UnifiedNewsContent>> cachedContent;
    private final Map<NewsCategory, FetchMetadata> fetchMetadata;
    private final boolean retryEnabled;
    private final int retryDelaySeconds;

    /**
     * News categories supported by the system.
     */
    public enum NewsCategory {
        GENERAL("general", "News Update"),
        CRYPTO("crypto", "Financial Update"),
        SPORTS("sports", "Sports Update"),
        ENTERTAINMENT("entertainment", "Entertainment Update"),
        TECH("tech", "Tech Update");

        private final String key;
        private final String displayName;

        NewsCategory(String key, String displayName) {
            this.key = key;
            this.displayName = displayName;
        }

        public String getKey() {
            return key;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    public UnifiedNewsService(Properties properties) {
        this.grokClient = new GrokClient(properties);
        this.retryEnabled = Boolean.parseBoolean(properties.getProperty("grok.retry.enabled", "true"));
        this.retryDelaySeconds = Integer.parseInt(properties.getProperty("grok.retry.delay.seconds", "10"));

        // Initialize unified persistence
        String baseDir = properties.getProperty("news.base.persist.dir", "storage/news");
        this.persistence = new UnifiedNewsPersistence(baseDir);

        // Initialize data structures
        this.categoryConfigs = new ConcurrentHashMap<>();
        this.cachedContent = new ConcurrentHashMap<>();
        this.fetchMetadata = new ConcurrentHashMap<>();

        // Create single shared scheduler
        this.scheduler = Executors.newScheduledThreadPool(
                NewsCategory.values().length,
                r -> {
                    Thread thread = new Thread(r, "UnifiedNews-Fetcher");
                    thread.setDaemon(true);
                    return thread;
                }
        );

        // Load configurations for all categories
        loadCategoryConfigurations(properties);

        // Initialize all categories
        for (NewsCategory category : NewsCategory.values()) {
            initializeCategory(category);
        }

        LoggerUtil.info("UnifiedNewsService initialized with " + NewsCategory.values().length + " categories");
    }

    /**
     * Load configuration for all news categories from properties.
     */
    private void loadCategoryConfigurations(Properties properties) {
        // Global defaults
        int defaultRefresh = Integer.parseInt(
                properties.getProperty("news.default.refresh.interval.minutes",
                        String.valueOf(DEFAULT_REFRESH_INTERVAL_MINUTES)));
        int defaultQuota = Integer.parseInt(properties.getProperty("news.default.daily.quota", "3"));
        int defaultKeepCount = Integer.parseInt(properties.getProperty("news.default.keep.count", "10"));

        // Story formatting (global)
        int storySentences = Integer.parseInt(properties.getProperty("grok.story.summary.sentences", "4"));
        int headlineMin = Integer.parseInt(properties.getProperty("grok.story.headline.words.min", "12"));
        int headlineMax = Integer.parseInt(properties.getProperty("grok.story.headline.words.max", "18"));

        String baseDir = properties.getProperty("news.base.persist.dir", "storage/news");

        for (NewsCategory category : NewsCategory.values()) {
            String key = category.getKey();

            // Category-specific overrides
            int refreshInterval = Integer.parseInt(
                    properties.getProperty("news." + key + ".refresh.interval.minutes",
                            properties.getProperty("grok." + key + ".refresh.interval.minutes",
                                    String.valueOf(defaultRefresh))));

            int quota = Integer.parseInt(
                    properties.getProperty("news." + key + ".daily.quota",
                            String.valueOf(defaultQuota)));

            int keepCount = Integer.parseInt(
                    properties.getProperty("news." + key + ".keep.count",
                            properties.getProperty("grok." + key + ".keep.count",
                                    String.valueOf(defaultKeepCount))));

            String persistDir = properties.getProperty("news." + key + ".persist.dir",
                    properties.getProperty("grok." + key + ".persist.dir",
                            baseDir + "/" + key));

            // Build prompt based on category
            String prompt = buildPromptForCategory(category, storySentences, headlineMin, headlineMax);

            NewsConfig config = new NewsConfig.Builder()
                    .categoryName(key)
                    .prompt(prompt)
                    .refreshIntervalMinutes(refreshInterval)
                    .dailyQuota(quota)
                    .storySentences(storySentences)
                    .headlineWordsMin(headlineMin)
                    .headlineWordsMax(headlineMax)
                    .fallbackMessage("No " + key + " news available")
                    .persistDirectory(persistDir)
                    .keepFileCount(keepCount)
                    .build();

            categoryConfigs.put(category, config);
            LoggerUtil.info("Loaded config for " + key + ": " + config);
        }
    }

    /**
     * Build the Grok AI prompt for a specific category.
     */
    private String buildPromptForCategory(NewsCategory category, int storySentences, int headlineMin, int headlineMax) {
        LocalDate today = LocalDate.now();
        String dateStr = today.format(DateTimeFormatter.ofPattern("MMMM d, yyyy"));

        String basePrompt = "You are a news reporter for a retro Dialtone interface. " +
                "TODAY IS " + dateStr + ". " +
                "Provide 3 significant stories FROM THE LAST 24 HOURS covering ";

        String focus = switch (category) {
            case GENERAL -> "major world events, politics, and breaking news. " +
                    "Use your Live Search capability to find RECENT news from X/Twitter, news outlets, and international sources. " +
                    "\n\n" +
                    "EDITORIAL FOCUS:\n" +
                    "- Diverse perspectives from multiple sources\n" +
                    "- International developments and geopolitics\n" +
                    "- Factual reporting with relevant context\n" +
                    "- Emphasize stories that matter to informed citizens\n" +
                    "- Skip celebrity gossip unless broadly significant\n";

            case CRYPTO -> "markets, economy, crypto, and monetary policy. " +
                    "Use your Live Search capability to find RECENT financial news from X/Twitter, analysts, and market sources. " +
                    "\n\n" +
                    "EDITORIAL FOCUS:\n" +
                    "- Market movements and investor-relevant developments\n" +
                    "- Monetary policy impacts (Fed, inflation, rates)\n" +
                    "- Crypto adoption and blockchain developments\n" +
                    "- Stock market developments and earnings\n" +
                    "- Factual analysis with relevant market context\n";

            case SPORTS -> "major sports events, scores, and athlete news. " +
                    "Use your Live Search capability to find RECENT sports news from X/Twitter, team sources, and sports media. " +
                    "\n\n" +
                    "EDITORIAL FOCUS:\n" +
                    "- Major game results and playoff implications\n" +
                    "- Notable athlete performances and records\n" +
                    "- Team standings and season developments\n" +
                    "- Avoid gossip unless directly related to on-field impact\n" +
                    "- Emphasize competitive spirit and athletic achievement\n";

            case ENTERTAINMENT -> "entertainment news, movies, TV, music, and pop culture. " +
                    "Use your Live Search capability to find RECENT entertainment news from X/Twitter, industry sources, and fan communities. " +
                    "\n\n" +
                    "EDITORIAL FOCUS:\n" +
                    "- Major releases, box office, and streaming hits\n" +
                    "- Awards and industry recognition\n" +
                    "- Notable performances and creative work\n" +
                    "- Avoid excessive celebrity gossip\n" +
                    "- Focus on entertainment value and cultural impact\n";

            case TECH -> "AI/ML breakthroughs, tech innovation, and major tech developments. " +
                    "Use your Live Search capability to find RECENT tech news from X/Twitter, research institutions, and tech industry sources. " +
                    "\n\n" +
                    "EDITORIAL FOCUS:\n" +
                    "- AI research and model releases\n" +
                    "- Machine learning breakthroughs\n" +
                    "- Tech innovation and product launches\n" +
                    "- Open source developments\n" +
                    "- Avoid corporate PR fluff\n" +
                    "- Emphasize technical merit and real-world impact\n";
        };

        String teaserNote = category == NewsCategory.CRYPTO ?
                "The teaser headline should be concise because it will be displayed alongside the Bitcoin price. " :
                "The teaser headline should be compelling and informative. ";

        return basePrompt + focus +
                "\n" +
                "Format your response EXACTLY as follows:\n" +
                "TEASER: [A " + headlineMin + "-" + headlineMax + " word headline about the most significant story]\n" +
                "\n" +
                "STORY 1: [Headline]\n" +
                "[" + storySentences + "-sentence summary with specific details and context]\n" +
                "\n" +
                "STORY 2: [Headline]\n" +
                "[" + storySentences + "-sentence summary with specific details and context]\n" +
                "\n" +
                "STORY 3: [Headline]\n" +
                "[" + storySentences + "-sentence summary with specific details and context]\n" +
                "\n" +
                teaserNote +
                "Story headlines should be descriptive.";
    }

    /**
     * Initialize a single category: load existing content and start background fetching.
     */
    private void initializeCategory(NewsCategory category) {
        NewsConfig config = categoryConfigs.get(category);
        String key = category.getKey();

        LoggerUtil.info("Initializing " + key + " news (refresh interval: " +
                config.getRefreshIntervalMinutes() + " minutes)");

        // Initialize cache
        cachedContent.put(category, new AtomicReference<>());

        // Load existing content
        UnifiedNewsContent existingContent = persistence.loadLatestContent(key);
        if (existingContent != null) {
            cachedContent.get(category).set(existingContent);
            LoggerUtil.info("Loaded existing " + key + " news: " + existingContent.getTeaserHeadline());
        } else {
            LoggerUtil.warn("No existing " + key + " news found - will show default message until first fetch succeeds");
        }

        // Load fetch metadata
        FetchMetadata metadata = persistence.loadFetchMetadata(key, config.getDailyQuota());
        if (metadata == null) {
            metadata = new FetchMetadata(config.getDailyQuota());
        }
        metadata.resetIfNewDay();

        // Persist the reset date immediately to prevent it getting stuck
        // This ensures date changes are saved even if no fetch occurs (e.g., quota exhausted)
        persistence.saveFetchMetadata(key, metadata);

        fetchMetadata.put(category, metadata);

        // Start background fetching
        startBackgroundFetching(category);
    }

    /**
     * Start background fetching for a category.
     */
    private void startBackgroundFetching(NewsCategory category) {
        NewsConfig config = categoryConfigs.get(category);
        FetchMetadata metadata = fetchMetadata.get(category);

        LoggerUtil.info("Starting " + category.getKey() + " background fetching (quota: " +
                config.getDailyQuota() + "/day)");

        long initialDelayMinutes = calculateInitialDelay(category);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                fetchAndUpdate(category);
            } catch (Exception e) {
                LoggerUtil.error(category.getKey() + " background fetch failed: " + e.getMessage());
            }
        }, initialDelayMinutes, config.getRefreshIntervalMinutes(), TimeUnit.MINUTES);

        LoggerUtil.info("Scheduled " + category.getKey() + " to fetch in " + initialDelayMinutes +
                " minutes, then every " + config.getRefreshIntervalMinutes() + " minutes (quota: " +
                metadata.getRemainingToday() + "/" + config.getDailyQuota() + " remaining today)");
    }

    /**
     * Calculate smart initial delay for a category.
     */
    private long calculateInitialDelay(NewsCategory category) {
        FetchMetadata metadata = fetchMetadata.get(category);
        NewsConfig config = categoryConfigs.get(category);
        AtomicReference<UnifiedNewsContent> cache = cachedContent.get(category);

        // If we have cached content and quota is exhausted, delay until tomorrow
        if (cache.get() != null && !metadata.canFetch()) {
            long minutesUntilMidnight = ChronoUnit.MINUTES.between(
                    Instant.now(),
                    LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            );
            LoggerUtil.info("Daily quota exhausted (" + metadata.getFetchCountToday() + "/" +
                    config.getDailyQuota() + "), delaying " + category.getKey() + " until tomorrow (+" +
                    minutesUntilMidnight + " minutes)");
            return minutesUntilMidnight + 1;
        }

        // If no cached content, fetch immediately
        if (cache.get() == null) {
            LoggerUtil.info("No cached " + category.getKey() + " content, will attempt immediate fetch");
            return 0;
        }

        // Check age of cached content
        UnifiedNewsContent content = cache.get();
        Instant generatedAt = content.getGeneratedAtInstant();

        if (generatedAt != null) {
            long contentAgeMinutes = ChronoUnit.MINUTES.between(generatedAt, Instant.now());
            long refreshIntervalMinutes = config.getRefreshIntervalMinutes();

            // If content is older than refresh interval, fetch immediately
            if (contentAgeMinutes >= refreshIntervalMinutes) {
                LoggerUtil.info("Cached " + category.getKey() + " content is stale (" +
                        contentAgeMinutes + " minutes old, refresh interval: " +
                        refreshIntervalMinutes + " minutes), will fetch immediately");
                return 0;
            }

            // If content is fresh, delay by remaining time until next refresh
            long remainingMinutes = refreshIntervalMinutes - contentAgeMinutes;
            LoggerUtil.info("Cached " + category.getKey() + " content is " + contentAgeMinutes +
                    " minutes old, delaying initial fetch by " + remainingMinutes + " minutes");
            return remainingMinutes;
        }

        // If we have content but no valid timestamp (legacy content), use configured interval
        LoggerUtil.info("Have cached " + category.getKey() + " content (no timestamp), delaying initial fetch by " +
                config.getRefreshIntervalMinutes() + " minutes");
        return config.getRefreshIntervalMinutes();
    }

    /**
     * Fetch and update content for a category.
     */
    private void fetchAndUpdate(NewsCategory category) {
        NewsConfig config = categoryConfigs.get(category);
        FetchMetadata metadata = fetchMetadata.get(category);

        // Reset counter if it's a new day
        metadata.resetIfNewDay();

        // Persist the reset date immediately to prevent it getting stuck
        // This ensures date changes are saved even if quota is exhausted and fetch is skipped
        persistence.saveFetchMetadata(category.getKey(), metadata);

        // Check quota
        if (!metadata.canFetch()) {
            LoggerUtil.info("Skipping " + category.getKey() + " fetch - daily quota exhausted (" +
                    metadata.getFetchCountToday() + "/" + config.getDailyQuota() + ")");
            return;
        }

        try {
            LoggerUtil.info("Fetching " + category.getKey() + " from Grok AI... (quota: " +
                    (metadata.getFetchCountToday() + 1) + "/" + config.getDailyQuota() + " today)");
            fetchWithRetry(category);

            // Record successful fetch
            metadata.recordFetch();
            persistence.saveFetchMetadata(category.getKey(), metadata);
            LoggerUtil.info(category.getKey() + " fetch complete. Remaining quota today: " +
                    metadata.getRemainingToday() + "/" + config.getDailyQuota());
        } catch (Exception e) {
            LoggerUtil.error("Failed to fetch " + category.getKey() + " from Grok after all retries: " + e.getMessage());
        }
    }

    /**
     * Fetch content with retry logic.
     */
    private void fetchWithRetry(NewsCategory category) throws Exception {
        Exception lastException = null;

        try {
            performFetch(category);
            return;
        } catch (Exception e) {
            lastException = e;
            String errorMsg = e.getMessage() != null ? e.getMessage() : "";

            // Check for HTTP 429 (rate limit) - don't retry permanent failures
            if (errorMsg.contains("HTTP 429")) {
                LoggerUtil.error("Grok API rate limit error (HTTP 429) for " + category.getKey() + " - skipping retry");
                throw e;
            }

            LoggerUtil.warn("Grok API call failed for " + category.getKey() + ": " + e.getMessage());
        }

        if (retryEnabled) {
            LoggerUtil.info("Retrying Grok API call for " + category.getKey() + " in " + retryDelaySeconds + " seconds...");
            try {
                Thread.sleep(retryDelaySeconds * 1000L);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new Exception("Retry interrupted", ie);
            }

            try {
                performFetch(category);
                LoggerUtil.info("Grok API retry succeeded for " + category.getKey());
                return;
            } catch (Exception e) {
                lastException = e;
                LoggerUtil.error("Grok API retry failed for " + category.getKey() + ": " + e.getMessage());
            }
        }

        throw lastException;
    }

    /**
     * Perform a single fetch operation for a category.
     */
    private void performFetch(NewsCategory category) throws Exception {
        NewsConfig config = categoryConfigs.get(category);
        String prompt = config.getPrompt();

        GrokChatRequest request = grokClient.createRequest(prompt);

        // Enable Live Search
        SearchParameters searchParams = grokClient.buildSearchParameters();
        if (searchParams != null) {
            request.setSearchParameters(searchParams);
            LoggerUtil.info("Live Search enabled for " + category.getKey() + " generation");
        }

        GrokChatResponse response = grokClient.chatCompletion(request);
        String content = grokClient.extractContent(response);

        if (content == null || content.isEmpty()) {
            throw new IOException("Grok returned empty content for " + category.getKey());
        }

        LoggerUtil.debug(() -> "Grok " + category.getKey() + " response: " + content);

        UnifiedNewsContent parsedContent = parseResponse(category, content);
        cachedContent.get(category).set(parsedContent);

        persistence.saveContent(category.getKey(), parsedContent, config.getKeepFileCount());

        LoggerUtil.info(capitalize(category.getKey()) + " updated: " + parsedContent.getTeaserHeadline());
    }

    /**
     * Parse Grok response into UnifiedNewsContent.
     */
    private UnifiedNewsContent parseResponse(NewsCategory category, String content) {
        String teaserHeadline = extractTeaser(content);
        List<UnifiedNewsContent.Story> stories = extractStories(content);

        UnifiedNewsContent newsContent = new UnifiedNewsContent();
        newsContent.setCategory(category.getKey());
        newsContent.setTeaserHeadline(teaserHeadline);
        newsContent.setStories(stories);
        newsContent.setFullReport(content);
        newsContent.setFallbackMessage(categoryConfigs.get(category).getFallbackMessage());

        return newsContent;
    }

    /**
     * Extract teaser headline from Grok response.
     */
    private String extractTeaser(String content) {
        Pattern teaserPattern = Pattern.compile("TEASER:\\s*(.+?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = teaserPattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        return "Top story today";
    }

    /**
     * Extract individual stories from Grok response.
     */
    private List<UnifiedNewsContent.Story> extractStories(String content) {
        List<UnifiedNewsContent.Story> stories = new ArrayList<>();

        Pattern storyPattern = Pattern.compile(
                "STORY \\d+:\\s*(.+?)\\n(.+?)(?=STORY \\d+:|$)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher matcher = storyPattern.matcher(content);

        while (matcher.find()) {
            String headline = matcher.group(1).trim();
            String summary = matcher.group(2).trim();
            summary = summary.replaceAll("\\n+", " ");
            stories.add(new UnifiedNewsContent.Story(headline, summary));
        }

        return stories;
    }

    /**
     * Get teaser headline for a category.
     * Returns instantly with cached value or fallback.
     */
    public String getTeaserHeadline(NewsCategory category) {
        UnifiedNewsContent content = cachedContent.get(category).get();

        if (content == null) {
            // Try loading historical content
            UnifiedNewsContent historical = persistence.loadLatestContent(category.getKey());
            if (historical != null) {
                LoggerUtil.info("Using historical " + category.getKey() + " content (waiting for quota/refresh)");
                cachedContent.get(category).set(historical);
                return historical.getTeaserHeadline();
            }
            return DEFAULT_LOADING_MESSAGE;
        }

        return content.getTeaserHeadline();
    }

    /**
     * Get latest content for a category.
     */
    public UnifiedNewsContent getLatestContent(NewsCategory category) {
        return cachedContent.get(category).get();
    }

    /**
     * Get full report for a category (legacy compatibility).
     */
    public String getFullReport(NewsCategory category) {
        UnifiedNewsContent content = getLatestContent(category);
        if (content == null) {
            return "Unavailable";
        }
        return content.getFullReport();
    }

    /**
     * Get headline for a category (CryptoNewsService compatibility).
     */
    public String getHeadline(NewsCategory category) {
        UnifiedNewsContent content = getLatestContent(category);
        if (content == null) {
            return DEFAULT_LOADING_MESSAGE;
        }
        return content.getHeadline();
    }

    @Override
    public void close() throws IOException {
        LoggerUtil.info("Shutting down UnifiedNewsService");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        grokClient.close();
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
