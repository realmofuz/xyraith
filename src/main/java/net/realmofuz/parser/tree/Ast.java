package net.realmofuz.parser.tree;

import net.realmofuz.parser.SpanData;
import net.realmofuz.util.ResourceLocation;

import java.util.HashMap;
import java.util.List;

public sealed interface Ast {
    sealed interface Value extends Ast {
        record Number(
            double number,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return Double.toString(this.number());
            }

            public Type type() {
                return new Type.Number();
            }

            @Override
            public java.lang.String toNbt() {
                return this.toString();
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return this.toString();
            }
        }

        record String(
            java.lang.String value,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return '"' + this.value() + '"';
            }

            public Type type() {
                return new Type.String();
            }

            @Override
            public java.lang.String toNbt() {
                return this.toString();
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return this.toString();
            }
        }

        record Literal(
            java.lang.String value,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return 'L' + '"' + this.value() + '"';
            }

            public Type type() {
                return new Type.Literal();
            }

            @Override
            public java.lang.String toNbt() {
                return this.toString();
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return this.toString();
            }
        }

        record ResourceLocationValue(
            ResourceLocation value,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return "RL\"" + this.value() + '"';
            }

            public Type type() {
                return new Type.ResourceLocation();
            }

            @Override
            public java.lang.String toNbt() {
                return this.toString();
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return this.toString();
            }
        }

        record Selection(
            Character value,
            Value.Component filter,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                if(filter == null)
                    return "@" + this.value;
                return '@' + this.value() + filter.toStructuredComponent();
            }

            public Type type() {
                return new Type.Selector();
            }

            @Override
            public java.lang.String toNbt() {
                return "";
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return "";
            }
        }

        record Boolean(
            boolean value,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return java.lang.Boolean.toString(value);
            }

            public Type type() {
                return new Type.Boolean();
            }

            @Override
            public java.lang.String toNbt() {
                if (value())
                    return "1b";
                return "0b";
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return this.toString();
            }
        }

        record Block(
            List<Command> commands,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return this.commands.toString();
            }

            public Type type() {
                return new Type.Block();
            }

            @Override
            public java.lang.String toNbt() {
                return '"' + this.toString() + '"';
            }

            @Override
            public java.lang.String toStructuredComponent() {
                return '"' + this.toString() + '"';
            }
        }

        record Component(
            HashMap<java.lang.String, Ast.Value> component,
            SpanData span
        ) implements Value {
            @Override
            public java.lang.String toString() {
                return this.component().toString();
            }

            public Type type() {
                return new Type.DynamicComponent();
            }

            @SuppressWarnings("unchecked")
            public<T> T get(java.lang.String key) {
                return (T) component.get(key);
            }

            public void put(java.lang.String key, Ast.Value value) {
                component.put(key, value);
            }

            public boolean has(java.lang.String key) {
                return component.containsKey(key);
            }

            @Override
            public java.lang.String toNbt() {
                var sb = new StringBuilder();
                sb.append('{');
                int index = 0;
                for (var key : this.component().keySet()) {
                    var value = this.component().get(key);
                    sb.append(key);
                    sb.append(":");
                    sb.append(value.toNbt());
                    if (index != this.component().keySet().size() - 1)
                        sb.append(',');
                    index++;
                }
                sb.append('}');
                return sb.toString();
            }

            @Override
            public java.lang.String toStructuredComponent() {
                var sb = new StringBuilder();
                sb.append('[');
                int index = 0;
                for (var key : this.component().keySet()) {
                    var value = this.component().get(key);
                    sb.append(key);
                    sb.append('=');
                    sb.append(value.toStructuredComponent());
                    if (index != this.component().keySet().size() - 1)
                        sb.append(',');
                    index++;
                }
                sb.append(']');
                return sb.toString();
            }
        }
    }

    record Command(
        String name,
        List<Value> values,
        SpanData span
    ) implements Ast {
        public Type type() {
            return new Type.Command();
        }

        @Override
        public String toNbt() {
            return "";
        }

        @Override
        public String toStructuredComponent() {
            return "";
        }
    }

    record File(
        List<Command> command,
        SpanData span
    ) implements Ast {
        public Type type() {
            return new Type.File();
        }

        @Override
        public String toNbt() {
            return "";
        }

        @Override
        public String toStructuredComponent() {
            return "";
        }
    }

    Type type();

    String toNbt();

    String toStructuredComponent();
    SpanData span();
}
