package com.app.mycity.data.model;

import com.google.firebase.firestore.DocumentId;
import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.IgnoreExtraProperties;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@IgnoreExtraProperties
public class Issue {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_RESOLVED = "RESOLVED";

    @DocumentId
    private String id;

    private String authorId;
    private String authorName;
    private String authorContact;
    private String title;
    private String description;
    private List<String> photoUrls = new ArrayList<>();
    private double lat;
    private double lng;
    private String address;
    private String status = STATUS_ACTIVE;
    private Date createdAt;
    private Date resolvedAt;
    private String resolvedBy;
    private String resolveReport;
    private List<String> reportPhotoUrls = new ArrayList<>();
    private int commentCount;

    public Issue() { }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorName() { return authorName; }
    public void setAuthorName(String authorName) { this.authorName = authorName; }

    public String getAuthorContact() { return authorContact; }
    public void setAuthorContact(String authorContact) { this.authorContact = authorContact; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getPhotoUrls() { return photoUrls; }
    public void setPhotoUrls(List<String> photoUrls) { this.photoUrls = photoUrls; }

    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }

    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }

    public Date getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Date resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public String getResolveReport() { return resolveReport; }
    public void setResolveReport(String resolveReport) { this.resolveReport = resolveReport; }

    public List<String> getReportPhotoUrls() { return reportPhotoUrls; }
    public void setReportPhotoUrls(List<String> reportPhotoUrls) { this.reportPhotoUrls = reportPhotoUrls; }

    public int getCommentCount() { return commentCount; }
    public void setCommentCount(int commentCount) { this.commentCount = commentCount; }

    @Exclude
    public boolean isResolved() { return STATUS_RESOLVED.equals(status); }
}
