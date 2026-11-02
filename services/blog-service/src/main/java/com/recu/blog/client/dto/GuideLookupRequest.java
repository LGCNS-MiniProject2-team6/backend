package com.recu.blog.client.dto;

import java.util.List;

public record GuideLookupRequest(Integer profileVersion, List<Long> postIds) {}
