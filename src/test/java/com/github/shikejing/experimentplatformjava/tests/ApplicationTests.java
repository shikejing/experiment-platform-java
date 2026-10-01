package com.github.shikejing.experimentplatformjava.tests;

import com.github.shikejing.experiment.ExperimentPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Spring 上下文冒烟测试：所有组件可正常装配。
 */
@SpringBootTest(classes = ExperimentPlatformApplication.class)
public class ApplicationTests {

    @Test
    public void contextLoads() {
    }
}
