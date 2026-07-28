package com.muhammadminhaz.talkateeve;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
@Disabled("Needs live Postgres, Redis and a valid Gemini key. Enable manually for integration runs.")
class TalkateaveApplicationTests {

    @Test
    void contextLoads() {
    }

}
