package com.automate.app.api;

import com.google.gson.JsonObject;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.*;

/**
 * Retrofit API interface for all AutoMate backend endpoints.
 */
public interface ApiService {

    // ==================== AUTH ====================

    @POST("/api/login")
    Call<JsonObject> login(@Body Map<String, String> body);

    @POST("/api/register")
    Call<JsonObject> register(@Body Map<String, String> body);

    @GET("/api/profile")
    Call<JsonObject> getProfile(@Header("Authorization") String token);

    @POST("/api/update_fcm_token")
    Call<JsonObject> updateFcmToken(
            @Header("Authorization") String token,
            @Body Map<String, String> body);

    // ==================== QUEUE ====================

    @POST("/api/join_queue")
    Call<JsonObject> joinQueue(
            @Header("Authorization") String token,
            @Body Map<String, String> body);

    @GET("/api/queue_status")
    Call<JsonObject> getQueueStatus(@Header("Authorization") String token);

    @DELETE("/api/leave_queue")
    Call<JsonObject> leaveQueue(@Header("Authorization") String token);

    @GET("/api/driver_queue")
    Call<JsonObject> getDriverQueue(
            @Header("Authorization") String token,
            @Query("direction") String direction);

    @POST("/api/attendance")
    Call<JsonObject> markAttendance(
            @Header("Authorization") String token,
            @Body Map<String, Object> body);

    @POST("/api/start_trip")
    Call<JsonObject> startTrip(
            @Header("Authorization") String token,
            @Body Map<String, String> body);

    @GET("/api/queue_stats")
    Call<JsonObject> getQueueStats(@Header("Authorization") String token);

    @GET("/api/full_queue")
    Call<JsonObject> getFullQueue(
            @Header("Authorization") String token,
            @Query("direction") String direction);

    // ==================== COMPLAINTS ====================

    @POST("/api/complaint")
    Call<JsonObject> submitComplaint(
            @Header("Authorization") String token,
            @Body Map<String, String> body);

    @GET("/api/complaints")
    Call<JsonObject> getComplaints(@Header("Authorization") String token);

    // ==================== HEALTH ====================

    @GET("/api/health")
    Call<JsonObject> healthCheck();

    // ==================== SCHEDULE ====================

    @GET("/api/schedule")
    Call<JsonObject> getSchedule(@Header("Authorization") String token);
}
