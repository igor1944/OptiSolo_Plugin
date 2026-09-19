package ru.optisolo.world;

import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/** Генератор пустого мира (VOID): только пустота, без структур и мобов. */
public class VoidGenerator extends ChunkGenerator {

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random,
                              int chunkX, int chunkZ, ChunkData chunkData) {
        // пусто — ничего не генерируем
    }

    @Override
    public void generateSurface(WorldInfo worldInfo, Random random,
                                int chunkX, int chunkZ, ChunkData chunkData) {
        // пусто
    }

    @Override
    public void generateBedrock(WorldInfo worldInfo, Random random,
                                int chunkX, int chunkZ, ChunkData chunkData) {
        // пусто
    }

    @Override
    public void generateCaves(WorldInfo worldInfo, Random random,
                              int chunkX, int chunkZ, ChunkData chunkData) {
        // пусто
    }

    @Override
    public boolean shouldGenerateNoise() {
        return false;
    }

    @Override
    public boolean shouldGenerateSurface() {
        return false;
    }

    @Override
    public boolean shouldGenerateBedrock() {
        return false;
    }

    @Override
    public boolean shouldGenerateCaves() {
        return false;
    }

    @Override
    public boolean shouldGenerateDecorations() {
        return false;
    }

    @Override
    public boolean shouldGenerateMobs() {
        return false;
    }

    @Override
    public boolean shouldGenerateStructures() {
        return false;
    }
}
