package com.mapmate.domain.calculator;

import static org.junit.Assert.assertEquals;

import java.time.LocalTime;
import org.junit.Test;

public class DepartureTimeCalculatorTest {
    @Test
    public void calculate_returnsRecommendedDepartureTime() {
        DepartureTimeCalculator calculator = new DepartureTimeCalculator();

        LocalTime result = calculator.calculate(
                LocalTime.of(9, 0),
                42,
                6,
                5
        );

        assertEquals(LocalTime.of(8, 7), result);
    }
}
