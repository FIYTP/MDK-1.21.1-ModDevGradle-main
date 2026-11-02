package com.FIYTP.realmedicalmod.system;

public class BleedingSystem {
    public static int calculateBleeding(float damage, boolean isPerforation) {
        int base;
        if (damage <= 2.0f) {
            base = 10 + (int)(Math.random() * 20);   // 10~30
        } else if (damage <= 4.0f) {
            base = 30 + (int)(Math.random() * 30);   // 30~60
        } else if (damage <= 6.0f) {
            base = 60 + (int)(Math.random() * 60);   // 60~120
        } else if (damage <= 8.0f) {
            base = 120 + (int)(Math.random() * 100); // 120~220
        } else if (damage <= 10.0f) {
            base = 220 + (int)(Math.random() * 180); // 220~400
        } else {
            base = 400 + (int)(Math.random() * 300); // 400~700
        }
        if (isPerforation) {
            base = (int)(base * 1.5f);
        }
        return base;
    }
}