/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.fdo.dsl.builders;

import com.dialtone.fdo.dsl.FdoDslBuilder;
import com.dialtone.fdo.dsl.RenderingContext;
import com.dialtone.filebrowser.FileBrowserService;
import com.dialtone.filebrowser.FileBrowserService.BrowseResult;
import com.dialtone.filebrowser.FileBrowserService.FileEntry;

/**
 * FDO builder for file browser window.
 *
 * <p>Creates a non-modal window with:
 * <ul>
 *   <li>Title bar showing current path</li>
 *   <li>DSS_LIST with clickable file/folder entries</li>
 *   <li>Back button when not at root</li>
 *   <li>Status line showing item count</li>
 * </ul>
 *
 * <p>Window has GID 99-0-1. Each navigation closes and recreates the window
 * to ensure clean state.</p>
 */
public final class FileBrowserFdoBuilder implements FdoDslBuilder {

    private static final String GID = "file_browser";

    // Window GID for reference
    private static final String WINDOW_GID = "99-0-1";

    private final BrowseResult browseResult;
    private final FileBrowserService service;

    public FileBrowserFdoBuilder(BrowseResult browseResult, FileBrowserService service) {
        this(browseResult, service, false);
    }

    public FileBrowserFdoBuilder(BrowseResult browseResult, FileBrowserService service, boolean isUpdate) {
        if (browseResult == null) {
            throw new IllegalArgumentException("browseResult cannot be null");
        }
        if (service == null) {
            throw new IllegalArgumentException("service cannot be null");
        }
        this.browseResult = browseResult;
        this.service = service;
        // isUpdate parameter kept for API compatibility but ignored - we always recreate
    }

    @Override
    public String getGid() {
        return GID;
    }

    @Override
    public String getDescription() {
        return "File browser window with file/folder list";
    }

    @Override
    public String toSource(RenderingContext ctx) {
        return generateWindowFdo();
    }

    /**
     * Generate window FDO. Always closes existing window first, then creates fresh.
     */
    private String generateWindowFdo() {
        StringBuilder sb = new StringBuilder();

        String windowTitle = "File Browser - " + browseResult.currentPath();

        sb.append("uni_start_stream <00x>\n");

        // Close existing window if present
        sb.append("  man_preset_gid <").append(WINDOW_GID).append(">\n");
        sb.append("  if_last_return_false_then <1>\n");
        sb.append("  man_close <").append(WINDOW_GID).append(">\n");
        sb.append("  uni_sync_skip <1>\n");

        // Create new window
        sb.append("  man_start_object <ind_group, \"").append(escapeString(windowTitle)).append("\">\n");
        sb.append("    mat_object_id <").append(WINDOW_GID).append(">\n");
        sb.append("    mat_orientation <vff>\n");
        sb.append("    mat_position <center_center>\n");
        sb.append("    mat_bool_background_tile <yes>\n");
        sb.append("    mat_art_id <1-69-27256>\n");
        sb.append("    mat_bool_precise <yes>\n");
        sb.append("    mat_precise_width <400>\n");
        sb.append("    mat_precise_height <350>\n");

        // DSS_LIST for file entries
        sb.append("    man_start_object <dss_list, \"\">\n");
        sb.append("      mat_orientation <vff>\n");
        sb.append("      mat_bool_precise <yes>\n");
        sb.append("      mat_precise_width <380>\n");
        sb.append("      mat_precise_height <280>\n");
        sb.append("      mat_relative_tag <1>\n");
        sb.append("      mat_title_pos <left | top_or_left>\n");
        sb.append("      mat_font_id <courier>\n");
        sb.append("      mat_font_size <9>\n");

        // File entries - created FIRST, inherit action from parent list
        generateListEntries(sb);

        // Action defined on the LIST (after items) - pattern from 32-5448.fdo.txt
        // Items inherit this action via act_set_inheritance <02x>
        sb.append("      act_set_criterion <select>\n");
        sb.append("      act_append_action\n");
        sb.append("        <\n");
        sb.append("        uni_start_stream\n");
        sb.append("          man_set_context_relative <1>\n");  // Set context to clicked item
        sb.append("          de_start_extraction <0>\n");
        sb.append("          de_get_data_pointer\n");           // Get clicked item's title
        sb.append("          var_string_set_from_atom <A>\n");
        sb.append("          buf_set_token <\"FB\">\n");
        sb.append("          var_string_get <A>\n");
        sb.append("          uni_use_last_atom_string <de_data>\n");
        sb.append("          de_end_extraction\n");
        sb.append("          buf_close_buffer\n");
        sb.append("          man_end_context\n");
        sb.append("        uni_end_stream\n");
        sb.append("        >\n");

        sb.append("    man_end_object\n");

        // Button row (only if we have a Back button)
        boolean hasBack = browseResult.parentPath() != null;
        if (hasBack) {
            sb.append("    man_start_object <org_group, \"\">\n");
            sb.append("      mat_orientation <hcf>\n");
            sb.append("      mat_relative_tag <2>\n");
            sb.append("      man_start_object <trigger, \"Back\">\n");
            sb.append("        mat_font_id <arial>\n");
            sb.append("        mat_font_size <10>\n");
            sb.append("        mat_font_style <bold>\n");
            sb.append("        mat_color_face <200, 200, 200>\n");
            sb.append("        mat_color_text <0, 0, 0>\n");
            sb.append("        mat_trigger_style <place>\n");
            sb.append("        act_set_criterion <select>\n");
            sb.append("        act_replace_action\n");
            sb.append("          <\n");
            sb.append("          uni_start_stream\n");
            sb.append("            de_start_extraction <0>\n");
            sb.append("            buf_set_token <\"FB\">\n");
            sb.append("            de_data <\"BACK\">\n");
            sb.append("            de_end_extraction\n");
            sb.append("            buf_close_buffer\n");
            sb.append("          uni_end_stream\n");
            sb.append("          >\n");
            sb.append("      man_end_object\n");
            sb.append("    man_end_object\n");
        }

        // Status line
        String status = browseResult.totalItems() + " items";
        sb.append("    man_start_object <ornament, \"\">\n");
        sb.append("      mat_relative_tag <3>\n");
        sb.append("      mat_font_id <arial>\n");
        sb.append("      mat_font_size <8>\n");
        sb.append("      mat_title <\"").append(status).append("\">\n");
        sb.append("    man_end_object\n");

        sb.append("    man_update_display\n");
        sb.append("  man_end_object\n");

        sb.append("  uni_wait_off\n");
        sb.append("uni_end_stream <00x>\n");

        return sb.toString();
    }

    /**
     * Generate list entries. Items inherit action from parent list.
     * Title format is "D:name" or "F:name" - the title IS the payload.
     * Pattern from 32-5448.fdo.txt - items use act_set_inheritance to inherit parent action.
     */
    private void generateListEntries(StringBuilder sb) {
        if (browseResult.entries().isEmpty()) {
            sb.append("      man_start_object <trigger, \"\">\n");
            sb.append("        mat_title <\"(empty)\">\n");
            sb.append("        mat_bool_disabled <yes>\n");
            sb.append("      man_end_object\n");
        } else {
            boolean first = true;
            for (FileEntry entry : browseResult.entries()) {
                if (first) {
                    sb.append("      man_start_object <trigger, \"\">\n");
                    first = false;
                } else {
                    sb.append("      man_start_sibling <trigger, \"\">\n");
                }

                // Title IS the payload: "D:name" for directory, "F:name" for file
                String payload = (entry.isDirectory() ? "D:" : "F:") + entry.name();
                sb.append("        mat_title <\"").append(escapeString(payload)).append("\">\n");

                // Inherit action from parent list (action defined after items)
                sb.append("        act_set_inheritance <02x>\n");
            }
            sb.append("      man_end_object\n");
        }
    }

    private String escapeString(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}
