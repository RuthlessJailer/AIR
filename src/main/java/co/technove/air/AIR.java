package co.technove.air;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

// todo probably needs lists eventually
public class AIR {
    private final Map<String, Section> sections = new LinkedHashMap<>();

    private static class ManualObject {
        public final String key;
        public final List<String> comments;

        private ManualObject(String key, List<String> comments) {
            this.key = key;
            this.comments = comments == null ? new ArrayList<>() : comments;
        }
    }

    private static class Section extends ManualObject {
        public final Map<String, Value<?>> values;

        private Section(String key, List<String> comments) {
            super(key, comments);
            this.values = new LinkedHashMap<>();
        }

        public void add(String key, Value<?> value) {
            this.values.put(key, value);
            value.parent = this;
        }

        public <T> Value<T> get(String key, ValueType<T> type) {
            Value<?> val = this.values.computeIfAbsent(key, k -> {
                Value<T> value = new Value<>(type, k, null, null);
                value.parent = this;
                return value;
            });
            if (val.type != type) {
                throw InvalidConfigurationException.forValue("Failed to retrieve value for " + key + " of type " + type + " when type is already " + val.type,
                                                             this.key + "." + key,
                                                             (val.type == ValueType.STRING ? "\"" : "") + val.value + (val.type == ValueType.STRING ? "\"" : "")); // wrap with quotes (") if string
            }
            return (Value<T>) val;
        }
    }

    static class Value<T> extends ManualObject {
        public final ValueType<T> type;
        public T value;
        public Section parent;

        private Value(ValueType<T> type, String key, T value, List<String> comments) {
            super(key, comments);
            if (type == null) {
                throw new NullPointerException();
            }
            this.type = type;
            this.value = value;
        }

        public String serialize() {
            if (this.type == null) {
                throw new RuntimeException("Cannot serialize unknown value");
            }
            return this.type.serialize(this.value);
        }
    }

    public static class InvalidConfigurationException extends IllegalArgumentException /* for backwards-compatibility in case somebody is try/catching IllegalArgumentException */{

        private final String[] error;

        private InvalidConfigurationException(String[] error){
            // using the message here because when this exception is wrapped in another, printStackTrace is not called. it's not perfect but it works
            super(String.join("\n", error));
            this.error = error;
        }

        static InvalidConfigurationException forList(String message, String key, Value<?> value){
            String string = String.valueOf(value.value);
            if (value.type == ValueType.STRING) {
                string = '"' + string + '"';
            }
            String[] split = key.split("\\.");
            String[] error = new String[8];
            error[0] = message;
            error[1] = "[" + split[0] + "]";
            error[2] = "  " + split[1] + " = [";
            error[3] = "    ...,";
            error[4] = "    " + string + ",";
            error[5] = repeat(' ', error[4].length() - string.length() - 1 /* for the comma */) + repeat('^', string.length()) + " ";
            error[6] = "    ...";
            error[7] = "  ]";
            return new InvalidConfigurationException(error);
        }

        static InvalidConfigurationException forLine(String message, String line){
            String[] error = new String[3];
            error[0] = message;
            error[1] = line;
            error[2] = repeat('^', error[1].length());
            return new InvalidConfigurationException(error);
        }

        static InvalidConfigurationException forValue(String message, String key, Object value){
            String string = String.valueOf(value);
            String[] split = key.split("\\.");
            String[] error = new String[4];
            error[0] = message;
            error[1] = "[" + split[0] + "]";
            error[2] = "  " + split[1] + " = " + string;
            error[3] = repeat(' ', error[2].length() - string.length()) + repeat('^', string.length());
            return new InvalidConfigurationException(error);
        }

        public String getErrorMessage(){
            return String.join("\n", this.error);
        }

        public void printErrorMessage(){
            printErrorMessage(System.err);
        }

        public void printErrorMessage(PrintStream s){
            s.println(getMessage());
        }

        public void printErrorMessage(PrintWriter s){
            s.println(getMessage());
        }

        @Override
        public void printStackTrace(PrintStream s) {
            printErrorMessage(s);
            super.printStackTrace(s);
        }

        @Override
        public void printStackTrace(PrintWriter s) {
            printErrorMessage(s);
            super.printStackTrace(s);
        }

        private static String repeat(char character, int times) {
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < times; i++) {
                builder.append(character);
            }
            return builder.toString();
        }
    }

    public AIR(){}

    public AIR(InputStream stream) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream))) {
            Section currentSection = null;
            List<String> currentComment = new ArrayList<>();
            String listKey = null;
            List<Object> currentList = null;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.length() == 0) {
                    continue; // empty line
                }

                if (line.startsWith("#")) {
                    currentComment.add(line.substring(1).trim());
                } else if (line.startsWith("[")) {
                    if (!line.endsWith("]")) {
                        throw InvalidConfigurationException.forLine("Invalid configuration: section identifier does not end with ]", line);
                    }
                    if (line.length() < 3) {
                        throw InvalidConfigurationException.forLine("Invalid configuration: section identifier does not have a name", line);
                    }
                    String sectionName = line.substring(1, line.length() - 1);
                    Section newSection = new Section(sectionName, currentComment);
                    currentComment = new ArrayList<>();
                    currentSection = newSection;
                    this.sections.put(sectionName, newSection);
                } else {
                    if (currentSection == null) {
                        throw InvalidConfigurationException.forLine("Invalid configuration: found value outside of section", line);
                    }
                    String key;
                    String value;

                    if (currentList == null) {
                        int equals = line.indexOf("=");
                        if (equals <= 1 || equals == line.length() - 1) {
                            throw InvalidConfigurationException.forLine("Invalid configuration: assignment invalid", line);
                        }

                        key = line.substring(0, equals).trim();
                        value = line.substring(equals + 1).trim();

                        if (value.length() == 0) {
                            throw InvalidConfigurationException.forValue("Invalid configuration: value does not exist", currentSection.key + "." + key, null);
                        }
                        if (value.equals("[")) {
                            // start reading list
                            listKey = key;
                            currentList = new ArrayList<>();
                            continue;
                        }

                    } else {
                        key = null;
                        value = line.trim();

                        if (value.equals("]")) {
                            currentSection.add(listKey, new Value(ValueType.LIST, listKey, currentList, currentComment));
                            currentList = null;
                            listKey = null;
                            continue;
                        }

                        if (value.endsWith(",")) {
                            value = value.substring(0, value.length() - 1);
                        }
                    }

                    boolean found = false;
                    for (ValueType<?> valueType : ValueType.values) {
                        Optional<?> possible = valueType.apply(value);
                        if (possible.isPresent()) {
                            found = true;

                            if (currentList == null) {
                                currentSection.add(key, new Value(valueType, key, possible.get(), currentComment));
                            } else {
                                currentList.add(new Value(valueType, listKey, possible.get(), Collections.emptyList()));
                            }
                            break;
                        }
                    }
                    if (!found) {
                        throw currentList == null
                              ? InvalidConfigurationException.forValue("Invalid configuration: unknown type", currentSection.key + "." + key, value)
                              : InvalidConfigurationException.forLine("Invalid configuration: unknown type", line);
                    }

                    currentComment = new ArrayList<>();
                }
            }
        }
    }

    public void save(OutputStream stream) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stream))) {
            for (Map.Entry<String, Section> entry : this.sections.entrySet()) {
                Section section = entry.getValue();
                if (section.comments != null) {
                    for (String comment : section.comments) {
                        writer.write("# " + comment + "\n");
                    }
                }
                writer.write("[" + section.key + "]" + "\n");
                for (Value value : section.values.values()) {
                    if (value.comments != null) {
                        for (String comment : value.comments) {
                            writer.write("  # " + comment + "\n");
                        }
                    }
                    writer.write("  " + value.key + " = " + value.serialize() + "\n");
                }
                writer.write("\n");
            }
        }
    }

    private ManualObject getObject(ValueType<?> type, String key) {
        String[] split = key.split("\\.", 2);
        if (split.length == 1) {
            return this.sections.computeIfAbsent(key, k -> new Section(k, null));
        }
        return this.sections.computeIfAbsent(split[0], k -> new Section(k, null)).get(split[1], type);
    }

    public void setComment(String key, String... comment) {
        String[] split = key.split("\\.", 2);
        ManualObject object = this.sections.computeIfAbsent(split[0], v -> new Section(split[0], null));
        object.comments.clear();
        object.comments.addAll(Arrays.asList(comment));
    }

    private <T> T get(ValueType<T> type, String key, T defaultValue, String... comment) {
        String[] split = key.split("\\.", 2);
        if (split.length == 1) {
            throw new IllegalArgumentException("Key '" + key + "' does not include section");
        }
        Section section = this.sections.computeIfAbsent(split[0], k -> new Section(k, null));
        if (!section.values.containsKey(split[1])) {
            Value value = section.get(split[1], type);
            value.value = defaultValue;
            value.comments.addAll(Arrays.asList(comment));
            return defaultValue;
        }
        Value value = section.get(split[1], type);
        if (value.type != type) {
            throw InvalidConfigurationException.forValue("Failed to retrieve '" + key + "' because it already exists with type " + value.type + " when requested type is " + type, key, value.value);
        }
        if (value.comments.isEmpty()) {
            value.comments.addAll(Arrays.asList(comment));
        }
        return (T) value.value;
    }

    public boolean getBoolean(String key, boolean defaultValue, String... comment) {
        return this.get(ValueType.BOOL, key, defaultValue, comment);
    }

    public int getInt(String key, int defaultValue, String... comment) {
        return this.get(ValueType.INT, key, defaultValue, comment);
    }

    public double getDouble(String key, double defaultValue, String... comment) {
        return this.get(ValueType.DOUBLE, key, defaultValue, comment);
    }

    public String getString(String key, String defaultValue, String... comment) {
        return this.get(ValueType.STRING, key, defaultValue, comment);
    }

    public <T> List<T> getList(String key, ValueType<T> type, List<T> defaultValue, String... comment) throws IOException /* unnecessary, but a breaking change if removed */ {
        String[] split = key.split("\\.", 2);
        if (split.length == 1) {
            throw new IllegalArgumentException("Key '" + key + "' does not include section");
        }
        Section section = this.sections.computeIfAbsent(split[0], k -> new Section(k, null));
        if (!section.values.containsKey(split[1])) {
            Value<List<AIR.Value<?>>> value = section.get(split[1], ValueType.LIST);
            value.value = defaultValue.stream().map(val -> new Value<>(type, null, val, null)).collect(Collectors.toList());
            value.comments.addAll(Arrays.asList(comment));
            return defaultValue;
        }
        Value<List<AIR.Value<?>>> value = section.get(split[1], ValueType.LIST);
        if (value.comments.isEmpty()) {
            value.comments.addAll(Arrays.asList(comment));
        }
        List<Value<?>> list = value.value;
        for (Value<?> val : list) {
            if (val.type != type) {
                throw InvalidConfigurationException.forList("Found invalid type " + val.type + " when looking for " + type, key, val);
            }
        }
        return list.stream().map(val -> (T) val.value).collect(Collectors.toList());
    }

    public <T> void setList(ValueType<T> listType, String key, List<T> value) {
        ManualObject object = getObject(ValueType.LIST, key);
        if (!(object instanceof Value)) {
            throw new IllegalArgumentException("Invalid key for value " + key);
        }
        ((Value<List<Value<T>>>) object).value = value.stream().map(val -> new Value<T>(listType, null, val, null)).collect(Collectors.toList());
    }

    public <T> void set(ValueType<T> type, String key, T value) {
        ManualObject object = getObject(type, key);
        if (!(object instanceof Value)) {
            throw new IllegalArgumentException("Invalid key for value " + key);
        }
        ((Value) object).value = value;
    }

    public void merge(AIR defaults) {
        for (final Map.Entry<String, Section> defaultSection : defaults.sections.entrySet()) { // loop through default values
            Section section = this.sections.computeIfAbsent(defaultSection.getKey(), k -> defaultSection.getValue()); // merge sections
            if (section.comments.isEmpty()) { // copy over the comments for the section, if necessary
                section.comments.addAll(defaultSection.getValue().comments);
            }
            for (final Map.Entry<String, Value<?>> defaultValue : defaultSection.getValue().values.entrySet()) {
                Value<?> value = section.values.computeIfAbsent(defaultValue.getKey(), k -> defaultValue.getValue()); // merge values
                if(value.type != defaultValue.getValue().type){ // fix type difference (defaults take priority for type, but not value)
                    section.values.put(defaultValue.getKey(), defaultValue.getValue());
                }
                if (value.comments.isEmpty()) { // copy over the comments for the value, if necessary
                    value.comments.addAll(defaultValue.getValue().comments);
                }
            }
        }
    }

}
