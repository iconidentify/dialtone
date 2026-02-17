/*
 * Copyright (c) 2025 iconidentify. MIT License. See LICENSE file.
 */

package com.dialtone.unit.login;

import com.dialtone.fdo.FdoTemplateEngine;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class IdbUsernameListEncoderTest {

    @Test
    void shouldMatchKnownIdbAppendData() {
        Map<String, String> variables = FdoTemplateEngine.createUsernameConfigVariables("GuestU");
        String actual = variables.get("USERNAME_SELECTOR_BINARY");

        String expected = "idb_append_data <"
            + "47x, 75x, 65x, 73x, 74x, 55x, 20x, 20x, 20x, 20x, 00x, "
            + "20x, 20x, 20x, 20x, 20x, 20x, 20x, 20x, 20x, 20x, 00x, "
            + "47x, 75x, 65x, 73x, 74x, 20x, 20x, 20x, 20x, 20x, 00x, "
            + "37x, 37x, 37x, 37x, 37x, 37x, 37x, 37x, 37x, 37x, 00x, "
            + "4Ex, 65x, 77x, 20x, 55x, 73x, 65x, 72x, 20x, 20x, 00x, "
            + "36x, 36x, 36x, 36x, 36x, 36x, 36x, 36x, 36x, 36x, 00x, "
            + "4Ex, 65x, 77x, 20x, 4Cx, 6Fx, 63x, 61x, 6Cx, 23x, 00x, "
            + "35x, 35x, 35x, 35x, 35x, 35x, 35x, 35x, 35x, 35x, 00x>";

        assertEquals(expected, actual);
    }
}


