package co.technove.air;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

// todo way more comment & section tests
public class AIRTest {
    @Test
    public void simpleParseTest() throws IOException {
        String contents = "# Hello, World\n" +
                "[_head]\n" +
                "\n" +
                "[foo]\n" +
                "bar = \"wow\"";

        AIR parser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(parser.getString("foo.bar", "nonexistent"), "wow");
        Assertions.assertEquals(parser.getString("foo.bar2", "nonexistent"), "nonexistent");
    }

	@Test
	public void numericalParseTest() throws IOException {

		String contents = "# Hello, World\n" +
								"[_head]\n" +
								"\n" +
								"[foo]\n" +
								"bar = -1.5\n" +
								"baz = 76.1\n" +
								"buq = -53\n" +
								"qux = 12";

		AIR parser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertEquals(parser.getDouble("foo.bar", 0.0), -1.5);
		Assertions.assertEquals(parser.getDouble("foo.baz", 0.0), 76.1);
		Assertions.assertEquals(parser.getInt("foo.buq", 0), -53);
		Assertions.assertEquals(parser.getInt("foo.qux", 0), 12);
		Assertions.assertEquals(parser.getString("foo.bar2", "nonexistent"), "nonexistent");
	}

    @Test
    public void numericalListParseTest() throws IOException {
        String contents = "[section]\n" +
          "val = [\n" +
          "1\n" +
          "2\n" +
          "3\n" +
          "]";

        AIR parser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(parser.getList("section.val", ValueType.INT, null), Arrays.asList(1, 2, 3));
    }

    @Test
    public void stringListParseTest() throws IOException {
        String contents = "[section]\n" +
          "val = [\n" +
          "\"foo\"\n" +
          "\"bar\"\n" +
          "\"foo\"\n" +
          "]";

        AIR parser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
        Assertions.assertEquals(parser.getList("section.val", ValueType.STRING, null), Arrays.asList("foo", "bar", "foo"));
    }

    @Test
    public void mismatchedListTypeTest() throws IOException {
        String contents = "[section]\n" +
          "val = [\n" +
          "\"foo\"\n" +
          "1\n" +
          "true\n" +
          "]";

        AIR parser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () -> parser.getList("section.val", ValueType.DOUBLE, null));
    }

    @Test
    public void simpleWriteTest() throws IOException {
        AIR parser = new AIR();

        Assertions.assertEquals(parser.getString("foo.bar", "hello"), "hello");
        Assertions.assertEquals(parser.getString("foo.bar", "goodbye"), "hello");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        parser.save(outputStream);
        String conf = outputStream.toString(StandardCharsets.UTF_8.name());

        Assertions.assertEquals("[foo]\n  bar = \"hello\"\n\n", conf);
    }

    @Test
    public void listWriteTest() throws IOException {
        AIR parser = new AIR();

        List<String> list1 = Arrays.asList("foo", "bar", "hello", "world");
        List<Integer> list2 = Arrays.asList(1, 2, 3, 4, 5);
        List<Boolean> list3 = Arrays.asList(true, false, false, true, false);
        List<Double> list4 = Arrays.asList(234.1, -2132.2312313, 4334., 0.);

        parser.setList(ValueType.STRING, "lists.list1", list1);
        parser.setList(ValueType.INT, "lists.list2", list2);
        parser.setList(ValueType.BOOL, "lists.list3", list3);
        parser.setList(ValueType.DOUBLE, "lists.list4", list4);

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        parser.save(outputStream);
        String out = outputStream.toString(StandardCharsets.UTF_8.name());
        Assertions.assertEquals(out, "[lists]\n" +
          "  list1 = [\n" +
          "    \"foo\",\n" +
          "    \"bar\",\n" +
          "    \"hello\",\n" +
          "    \"world\",\n" +
          "  ]\n" +
          "  list2 = [\n" +
          "    1,\n" +
          "    2,\n" +
          "    3,\n" +
          "    4,\n" +
          "    5,\n" +
          "  ]\n" +
          "  list3 = [\n" +
          "    true,\n" +
          "    false,\n" +
          "    false,\n" +
          "    true,\n" +
          "    false,\n" +
          "  ]\n" +
          "  list4 = [\n" +
          "    234.1,\n" +
          "    -2132.2312313,\n" +
          "    4334.0,\n" +
          "    0.0,\n" +
          "  ]\n" +
          "\n");
    }

	@Test
	public void mergeTest() throws IOException {
		String defaults = "[foo]\n" +
						  "bar = \"qux\"\n" +
						  "baz = 15\n" +
						  "[qaz]\n" +
						  "bat = false";

		String contents = "[foo]\n" +
						  "baz = 2\n";

		AIR defaultsParser = new AIR(new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)));
		AIR contentsParser = new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8)));

		contentsParser.merge(defaultsParser);

		Assertions.assertEquals(contentsParser.getString("foo.bar", null), "qux");
		Assertions.assertEquals(contentsParser.getInt("foo.baz", 0), 2);
		Assertions.assertFalse(contentsParser.getBoolean("qaz.bat", true));
		Assertions.assertEquals(contentsParser.getString("foo.qux", "nonexistent"), "nonexistent");
	}

	@FunctionalInterface
	interface Thief<T, E extends Exception> {
		void steal(T t) throws E;
	}

	private void errorTest(String contents) throws Exception {
		errorTest(contents, (air) -> {});
	}

	private void errorTest(String contents, Thief<AIR, IOException> robber) throws Exception {
		try {
			robber.steal(new AIR(new ByteArrayInputStream(contents.getBytes(StandardCharsets.UTF_8))));
		}catch (AIR.InvalidConfigurationException e){
			e.printErrorMessage(System.out);
			throw e;
		}
	}

	@Test
	public void invalidValueTest() {
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () ->
				errorTest("[foo]\n" +
						  "bar = \"invalid value"));
	}

	@Test
	public void invalidSectionTest() {
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () ->
				errorTest("[invalid_section\n" +
						  "bar = \"qaz\""));
	}

	@Test
	public void invalidAssignmentTest() {
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () ->
				errorTest("[foo]\n" +
						  "an -> \"invalid assignment\""));
	}

	@Test
	public void unexpectedTypeTest() {
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () ->
				errorTest("[foo]\n" +
						  "bar = \"will retrieve an int\"", (air) -> air.getInt("foo.bar", 0)));
	}

	@Test
	public void differentListMemberTest() {
		Assertions.assertThrows(AIR.InvalidConfigurationException.class, () ->
				errorTest("[invalid]\n" +
						  "list = [\n" +
						  "-4.1,\n" +
						  "\"member\",\n" +
						  "15.3,\n" +
						  "]", (air) -> air.getList("invalid.list", ValueType.DOUBLE, null)));
	}
}
