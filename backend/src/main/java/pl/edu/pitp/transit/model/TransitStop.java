package pl.edu.pitp.transit.model;

public record TransitStop(
        int id,
        String feedId,
        String externalId,
        String name,
        String code,
        String platformCode,
        double lat,
        double lon
) {
}
