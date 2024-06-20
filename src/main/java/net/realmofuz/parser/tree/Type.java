package net.realmofuz.parser.tree;

import net.realmofuz.parser.map.ComponentDataRestriction;
import net.realmofuz.parser.map.CDRField;

import java.util.List;

public sealed interface Type {
    static Type LOCATION = new Type.RestrictedMap(new ComponentDataRestriction(List.of(
        new CDRField("x", new Type.Number(), false),
        new CDRField("y", new Type.Number(), false),
        new CDRField("z", new Type.Number(), false),
        new CDRField("pitch", new Type.Number(), true),
        new CDRField("yaw", new Type.Number(), true)
    )));
    static Type TEXT_COMPONENT = new Type.RestrictedMap(new ComponentDataRestriction(List.of(
        new CDRField("text", new Type.String(), true)
    )));
    static Type ITEM_COMPONENT = new Type.RestrictedMap(new ComponentDataRestriction(List.of(
        new CDRField("id", new Type.String(), false),
        new CDRField("count", new Type.Number(), false)
    )));
    static Type NUMBER_PROVIDER = new Type.RestrictedMap(new ComponentDataRestriction(List.of(
        new CDRField("type", new Type.String(), false),
        new CDRField("value", new Type.Number(), true),
        new CDRField("min", new Type.Number(), true),
        new CDRField("max", new Type.Number(), true),
        new CDRField("n", new Type.Number(), true),
        new CDRField("p", new Type.Number(), true),
        new CDRField("storage", new Type.String(), true),
        new CDRField("path", new Type.String(), true)
    )));

    static Type FILE = new Type.File();
    static Type COMMAND = new Type.Command();
    static Type BLOCK = new Type.Block();
    static Type STRING = new Type.String();
    static Type LITERAL = new Type.Literal();
    static Type RESOURCE_LOCATION = new Type.ResourceLocation();
    static Type SELECTOR = new Type.Selector();
    static Type NUMBER = new Type.Number();
    static Type BOOLEAN = new Type.Boolean();
    static Type DYNAMIC_MAP = new Type.DynamicComponent();
    static Type UNKNOWN = new Type.Unknown();


    record File() implements Type {
        @Override
        public boolean ifOtherIsSubtype(Type other) {
            return false;
        }
    }

    record Command() implements Type {
        @Override
        public boolean ifOtherIsSubtype(Type other) {
            return false;
        }
    }

    ;

    record Block() implements Type {
        @Override
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.Block;
        }
    }

    ;

    record String() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.String;
        }
    }

    ;

    record Literal() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.Literal;
        }
    }

    ;

    record ResourceLocation() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.ResourceLocation;
        }
    }

    ;

    record Selector() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.Selector;
        }
    }

    ;

    record Number() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.Number;
        }
    }

    ;

    record Boolean() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.Boolean;
        }
    }

    ;

    record DynamicComponent() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.DynamicComponent;
        }
    }

    ;

    record RestrictedMap(ComponentDataRestriction typeData) implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return other instanceof Type.RestrictedMap
                || other instanceof Type.DynamicComponent;
        }
    }

    ;

    record Unknown() implements Type {
        public boolean ifOtherIsSubtype(Type other) {
            return false;
        }
    }

    ;

    boolean ifOtherIsSubtype(Type other);
}
