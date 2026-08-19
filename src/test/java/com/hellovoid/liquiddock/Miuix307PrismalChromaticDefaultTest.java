package com.hellovoid.liquiddock;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Miuix307PrismalChromaticDefaultTest {
    @Test
    public void prismalMaterialDefaultChromaticStrengthIsTwo() {
        assertEquals(2f, Miuix307PrismalMaterial.defaults(1f).chromaticAberration, 0.0001f);
    }
}
