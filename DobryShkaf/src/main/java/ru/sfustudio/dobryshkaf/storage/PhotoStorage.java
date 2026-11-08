package ru.sfustudio.dobryshkaf.storage;

/** Хранилище файлов фотографий; в БД остаются только ключ и метаданные. */
public interface PhotoStorage {

    void put(String key, byte[] content, String contentType);

    byte[] get(String key);

    void delete(String key);
}
