package com.sopaletras.topic;

public record TopicDto(
        Long id,
        String slug,
        String name,
        String description,
        TopicType type,
        String category,
        String region,
        String icon,
        long wordCount,
        long playsThisWeek,
        boolean favorite) {

    public static TopicDto of(Topic t, long wordCount, long plays, boolean favorite) {
        return new TopicDto(t.getId(), t.getSlug(), t.getName(), t.getDescription(), t.getType(), t.getCategory(),
                t.getRegion(), t.getIcon(), wordCount, plays, favorite);
    }
}
