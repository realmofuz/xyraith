package net.realmofuz.util;

public sealed interface Result<T, E> {
    record Ok<T, E>(T value) implements Result<T, E> {}
    record Err<T, E>(E value) implements Result<T, E> {}
}
