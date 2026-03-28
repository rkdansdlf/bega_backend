package com.example.support;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

public final class HibernateQueryCountSupport {

    private HibernateQueryCountSupport() {
    }

    public static Statistics reset(EntityManagerFactory entityManagerFactory) {
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        if (!statistics.isStatisticsEnabled()) {
            throw new IllegalStateException("Hibernate statistics are not enabled for this persistence unit.");
        }
        statistics.clear();
        return statistics;
    }
}
