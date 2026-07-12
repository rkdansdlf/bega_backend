package com.example.common.clienterror;

public interface ClientErrorFeedbackTimeBucketProjection {

    Integer getBucketYear();

    Integer getBucketMonth();

    Integer getBucketDay();

    Integer getBucketHour();

    long getItemCount();
}
