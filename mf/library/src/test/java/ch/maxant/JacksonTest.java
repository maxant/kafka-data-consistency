    package ch.maxant;

    import com.fasterxml.jackson.core.JsonProcessingException;
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
    import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.Test;

    import static java.util.Collections.singletonList;
    import static org.junit.jupiter.api.Assertions.assertEquals;

    public class JacksonTest {

        ObjectMapper mapper = getMapper();
        Bear b = new Bear();

        @BeforeEach
        void setup() {
            b.setAge(5);
        }

        @Test
        public void testOne() throws JsonProcessingException {
            assertEquals("[{\"@c\":\"ch.maxant.Bear\",\"age\":5}]", mapper.writeValueAsString(singletonList(b)));
            assertEquals("{\"@c\":\"ch.maxant.Bear\",\"age\":5}", mapper.writeValueAsString(b)); // fails with Actual: {"age":5}
        }

        @Test
        public void testTwo() throws JsonProcessingException {
            String json = mapper.writeValueAsString(b); // {"age":5} - no type info!
            Animal a = mapper.readValue(json, Animal.class); // also fails, as mapper doesnt know if the json is a Bear or a Cat
            // the above line failed with:
            //      com.fasterxml.jackson.databind.exc.InvalidTypeIdException:
            //      Missing type id when trying to resolve subtype of
            //      [simple type, class ch.maxant.Animal]: missing type id property '@c'
            //       at [Source: (String)"{"age":5}"; line: 1, column: 9]
            assertEquals(5, a.getAge());
        }

        private ObjectMapper getMapper() {
            PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                    .allowIfSubTypeIsArray()
                    .allowIfBaseType("ch.maxant")
                    .allowIfSubType("ch.maxant")
                    .build();

            ObjectMapper mapper = new ObjectMapper();

            mapper.activateDefaultTypingAsProperty(
                    ptv,
                    ObjectMapper.DefaultTyping.NON_CONCRETE_AND_ARRAYS,
                    "@c"
            );

            return mapper;
        }
    }

    abstract class Animal {
        private int age;
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
    }
    class Bear extends Animal { }
    class Cat extends Animal { }
