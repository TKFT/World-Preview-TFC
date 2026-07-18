package com.rustysnail.world.preview.tfc.backend.worker;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkResultTest
{
    @Test
    void resultListsCannotBeNull()
    {
        assertThrows(NullPointerException.class, () -> new WorkResult(null, 0, null, null, List.of()));
        assertThrows(NullPointerException.class, () -> new WorkResult(null, 0, null, List.of(), null));
    }
}
