package com.autoauction.client.domain;

import java.util.Map;

public record AuctionItemRequest(String baseName, Map<String, Object> attributes) {
}
