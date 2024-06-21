package net.realmofuz.compile.contexts;

import net.realmofuz.parser.SpanData;
import net.realmofuz.parser.tree.Type;

/**
 * Represents a compilation error.
 */
public sealed interface CompileError {
    final class UnexpectedType extends Error implements CompileError {
        Type expectedType;
        Type found;
        SpanData span;

        public UnexpectedType(Type expectedType, Type found, SpanData span) {
            this.expectedType = expectedType;
            this.found = found;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "Expected " + expectedType + ", found " + found;
        }
    }

    final class NotACommand extends Error implements CompileError {
        String desired;
        SpanData span;

        public NotACommand(String desired, SpanData span) {
            this.desired = desired;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return desired + " is not a valid command.";
        }
    }

    final class UnexpectedLiteral extends Error implements CompileError {
        String found;
        SpanData span;

        public UnexpectedLiteral(String found, SpanData span) {
            this.found = found;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "Found unexpected literal " + found;
        }
    }

    final class UnexpectedEndOfCommand extends Error implements CompileError {
        Type desired;
        SpanData span;

        public UnexpectedEndOfCommand(Type desired, SpanData span) {
            this.desired = desired;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "Found end of command when wanting a " + desired;
        }
    }

    final class TooManyArguments extends Error implements CompileError {
        int amountDesired;
        int amountFound;
        SpanData span;

        public TooManyArguments(int amountDesired, int amountFound, SpanData span) {
            this.amountDesired = amountDesired;
            this.amountFound = amountFound;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "Too many arguments: wanted " + amountDesired + ", got " + amountFound;
        }
    }

    final class MapMissingKey extends Error implements CompileError {
        String missingKey;
        Type missingType;
        SpanData span;

        public MapMissingKey(String missingKey, Type missingType, SpanData span) {
            this.missingKey = missingKey;
            this.missingType = missingType;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "This map is missing key " + missingKey + " of type " + missingType;
        }
    }

    final class MapKeyBadType extends Error implements CompileError {
        String badKey;
        Type expectedType;
        Type foundType;
        SpanData span;

        public MapKeyBadType(String badKey, Type expectedType, Type foundType, SpanData span) {
            this.badKey = badKey;
            this.expectedType = expectedType;
            this.foundType = foundType;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "This map's key " + badKey + " is of type " + foundType + ", but expected type " + expectedType;
        }
    }

    final class UnexpectedMapKey extends Error implements CompileError {
        String badKey;
        SpanData span;

        public UnexpectedMapKey(String badKey, SpanData span) {
            this.badKey = badKey;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "This map's key " + badKey + " should not be present.";
        }
    }

    final class ReservedIdentifier extends Error implements CompileError {
        String badIdentifier;
        SpanData span;

        public ReservedIdentifier(String badIdentifier, SpanData span) {
            this.badIdentifier = badIdentifier;
            this.span = span;
        }

        @Override
        public SpanData span() {
            return this.span;
        }

        @Override
        public String errorLog() {
            return "The identifier " + badIdentifier + " is invalid as it is reserved with 2 underscores.";
        }
    }


    String errorLog();
    SpanData span();
}
