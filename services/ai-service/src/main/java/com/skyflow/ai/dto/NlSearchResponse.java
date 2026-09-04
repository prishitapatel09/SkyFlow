package com.skyflow.ai.dto;

import java.util.List;
import java.util.Map;

/**
 * @param criteria what the phrase was understood to mean, so the UI can show it and let the
 *                 traveller correct a wrong guess
 * @param flights  raw search payload from flight-service
 */
public record NlSearchResponse(SearchCriteria criteria, List<Map<String, Object>> flights,
                               int totalResults) {
}
