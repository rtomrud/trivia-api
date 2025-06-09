package com.example.trivia.util;

import org.springframework.web.util.UriComponentsBuilder;

public class LinkHeaderBuilder {

    /**
     * Generates a Link HTTP header string for pagination with self, next, prev,
     * first, and last links.
     *
     * @param pageNumber the current page number
     * @param size       the number of items per page
     * @param totalPages the total number of pages available
     * @param uri        the base URL for the endpoint
     * @return a Link header string with page and size query parameters
     */
    public static String buildWithPaginationLinks(int pageNumber, int size, int totalPages, String uri) {
        StringBuilder linkHeader = new StringBuilder();
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(uri != null ? uri : "/");

        String selfLink = builder.replaceQueryParam("page", pageNumber)
                .replaceQueryParam("size", size)
                .build()
                .toUriString();
        linkHeader.append(String.format("<%s>; rel=\"self\"", selfLink));

        if (pageNumber < totalPages - 1) {
            String nextLink = builder.replaceQueryParam("page", pageNumber + 1)
                    .replaceQueryParam("size", size)
                    .build()
                    .toUriString();
            linkHeader.append(", ").append(String.format("<%s>; rel=\"next\"", nextLink));
        }

        if (pageNumber > 0) {
            String prevLink = builder.replaceQueryParam("page", pageNumber - 1)
                    .replaceQueryParam("size", size)
                    .build()
                    .toUriString();
            linkHeader.append(", ").append(String.format("<%s>; rel=\"prev\"", prevLink));
        }

        String firstLink = builder.replaceQueryParam("page", 0)
                .replaceQueryParam("size", size)
                .build()
                .toUriString();
        linkHeader.append(", ").append(String.format("<%s>; rel=\"first\"", firstLink));

        String lastLink = builder.replaceQueryParam("page", totalPages - 1)
                .replaceQueryParam("size", size)
                .build()
                .toUriString();
        linkHeader.append(", ").append(String.format("<%s>; rel=\"last\"", lastLink));

        return linkHeader.toString();
    }
}
