package com.app.mycity.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class TransportRoute {

    public static final String TYPE_BUS = "BUS";
    public static final String TYPE_TRAM = "TRAM";
    public static final String TYPE_TROLLEYBUS = "TROLLEYBUS";
    public static final String TYPE_MARSHRUTKA = "MARSHRUTKA";

    @DocumentId
    private String id;

    private String routeNumber;
    private String type;
    private String name;
    private String color;
    private List<String> stopIds = new ArrayList<>();
    private List<GeoPoint> polyline = new ArrayList<>();
    private List<String> scheduleTimes = new ArrayList<>();
    private String gtfsRouteId;
    private boolean active;

    public TransportRoute() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getRouteNumber() { return routeNumber; }
    public void setRouteNumber(String routeNumber) { this.routeNumber = routeNumber; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public List<String> getStopIds() { return stopIds; }
    public void setStopIds(List<String> stopIds) { this.stopIds = stopIds; }

    public List<GeoPoint> getPolyline() { return polyline; }
    public void setPolyline(List<GeoPoint> polyline) { this.polyline = polyline; }

    public List<String> getScheduleTimes() { return scheduleTimes; }
    public void setScheduleTimes(List<String> scheduleTimes) { this.scheduleTimes = scheduleTimes; }

    public String getGtfsRouteId() { return gtfsRouteId; }
    public void setGtfsRouteId(String gtfsRouteId) { this.gtfsRouteId = gtfsRouteId; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    @Exclude
    public boolean hasSchedule() { return scheduleTimes != null && !scheduleTimes.isEmpty(); }
}
