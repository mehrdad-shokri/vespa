// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#include <vespa/vespalib/util/time.h>
#include <vespa/vespalib/gtest/gtest.h>
#include <cinttypes>
#include <thread>

using namespace vespalib;

TEST(TimeTest, steady_time_is_compatible_with_steady_clock) {
    steady_time t = steady_clock::now();
    (void) t;
}

TEST(TimeTest, system_time_is_compatible_with_system_clock) {
    system_time t = system_clock::now();
    (void) t;
}

TEST(TimeTest, timer_can_measure_elapsed_time) {
    Timer timer;
    std::this_thread::sleep_for(10ms);
    auto elapsed = timer.elapsed();
    EXPECT_GE(elapsed, 10ms);
    fprintf(stderr, "sleep(10ms) took %" PRId64 " us\n", count_us(elapsed));
}

TEST(TimeTest, double_conversion_works_as_expected) {
    EXPECT_EQ(to_s(10ms), 0.010);
    EXPECT_EQ(10ms, from_s(0.010));
}

TEST(TimeTest, timeval_conversion_works_as_expected) {
    timeval tv1;
    tv1.tv_sec = 7;
    tv1.tv_usec = 342356;
    EXPECT_EQ(from_timeval(tv1), 7342356us);
    tv1.tv_sec = 7;
    tv1.tv_usec = 1342356;
    EXPECT_EQ(from_timeval(tv1), 8342356us);
}

TEST(TimeTest, unit_counting_works_as_expected) {
    auto d = 7s + 3ms + 5us + 7ns;
    EXPECT_EQ(count_ns(d), 7003005007);
    EXPECT_EQ(count_us(d), 7003005);
    EXPECT_EQ(count_ms(d), 7003);
    EXPECT_EQ(count_s(d), 7);
}

TEST(TimeTest, to_string_print_iso_time) {
    EXPECT_EQ("1970-01-01 00:00:00.000 UTC", to_string(system_time()));
    EXPECT_EQ("2019-12-20 02:47:35.768 UTC", to_string(system_time(1576810055768543us)));
}

GTEST_MAIN_RUN_ALL_TESTS()
