package com.app.mycity.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.List;

@IgnoreExtraProperties
public class TransportStop {

    @DocumentId
    private String id;

    private String name;
    private double lat;
    private double lng;
    private List<String> routeIds = new ArrayList<>();
    private String gtfsStopId;

    public TransportStop() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public List<String> getRouteIds() { return routeIds; }
    public void setRouteIds(List<String> routeIds) { this.routeIds = routeIds; }

    public String getGtfsStopId() { return gtfsStopId; }
    public void setGtfsStopId(String gtfsStopId) { this.gtfsStopId = gtfsStopId; }
}
