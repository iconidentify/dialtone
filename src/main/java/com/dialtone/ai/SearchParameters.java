/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Search parameters for Grok API Live Search feature.
 * Enables real-time web, news, and X (Twitter) search capabilities.
 */
public class SearchParameters {

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("sources")
    private List<Source> sources;

    @JsonProperty("return_citations")
    private Boolean returnCitations;

    @JsonProperty("max_search_results")
    private Integer maxSearchResults;

    @JsonProperty("from_date")
    private String fromDate;

    @JsonProperty("to_date")
    private String toDate;

    public SearchParameters() {
        this.sources = new ArrayList<>();
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public List<Source> getSources() {
        return sources;
    }

    public void setSources(List<Source> sources) {
        this.sources = sources;
    }

    public Boolean getReturnCitations() {
        return returnCitations;
    }

    public void setReturnCitations(Boolean returnCitations) {
        this.returnCitations = returnCitations;
    }

    public Integer getMaxSearchResults() {
        return maxSearchResults;
    }

    public void setMaxSearchResults(Integer maxSearchResults) {
        this.maxSearchResults = maxSearchResults;
    }

    public String getFromDate() {
        return fromDate;
    }

    public void setFromDate(String fromDate) {
        this.fromDate = fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public void setToDate(String toDate) {
        this.toDate = toDate;
    }

    public void addSource(String type) {
        this.sources.add(new Source(type));
    }

    /**
     * Source object for search parameters.
     * Each source has a type (web, news, x, rss).
     */
    public static class Source {
        @JsonProperty("type")
        private String type;

        public Source() {}

        public Source(String type) {
            this.type = type;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }

    /**
     * Builder for creating SearchParameters with fluent API.
     */
    public static class Builder {
        private final SearchParameters params;

        public Builder() {
            this.params = new SearchParameters();
        }

        public Builder mode(String mode) {
            params.setMode(mode);
            return this;
        }

        public Builder addSource(String type) {
            params.addSource(type);
            return this;
        }

        public Builder returnCitations(boolean returnCitations) {
            params.setReturnCitations(returnCitations);
            return this;
        }

        public Builder maxSearchResults(int maxResults) {
            params.setMaxSearchResults(maxResults);
            return this;
        }

        public Builder fromDate(String fromDate) {
            params.setFromDate(fromDate);
            return this;
        }

        public Builder toDate(String toDate) {
            params.setToDate(toDate);
            return this;
        }

        public SearchParameters build() {
            return params;
        }
    }
}
