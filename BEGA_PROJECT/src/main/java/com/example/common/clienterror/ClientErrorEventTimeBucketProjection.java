package com.example.common.clienterror;

public interface ClientErrorEventTimeBucketProjection {

    Integer getBucketYear();

    Integer getBucketMonth();

    Integer getBucketDay();

    Integer getBucketHour();

    ClientErrorBucket getBucket();

    long getItemCount();
}
