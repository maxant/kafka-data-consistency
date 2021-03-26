package ch.maxant.kdc.ksql.udfs;

import io.confluent.ksql.function.udf.Udf;
import io.confluent.ksql.function.udf.UdfDescription;
import io.confluent.ksql.function.udf.UdfParameter;

import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.ZoneId;

@UdfDescription(name = "stringToTimestampIso8601", description = "same as StringToTimestamp, but without errors. see https://github.com/confluentinc/ksql/issues/4864")
public class StringToTimestampIso8601 {

    @Udf(description = "As per description")
    public long doit(@UdfParameter(value = "formattedTimestamp") String formattedTimestamp) {
        return LocalDateTime.parse(formattedTimestamp).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static void main(String[] args) throws ParseException {

        /*
ERROR {"type":1,"deserializationError":null,"recordProcessingError":{"errorMessage":"Error computing expression
STRINGTOTIMESTAMP('1996-12-31T23:59:59.999', 'yyyy-MM-dd''T''HH:mm:ss.SSS') for column A with index 4:
Failed to invoke function public long io.confluent.ksql.function.udf.datetime.StringToTimestamp.stringToTimestamp(java.lang.String,java.lang.String)",
"record":null,"cause":["Failed to invoke function public long io.confluent.ksql.function.udf.datetime.StringToTimestamp.stringToTimestamp(
java.lang.String,java.lang.String)","java.lang.reflect.InvocationTargetException","Failed to parse timestamp '1996-12-31T23:59:59.999'
with formatter 'yyyy-MM-dd'T'HH:mm:ss.SSS': Invalid date 'DayOfYear 366' as '1970' is not a leap year","Invalid date 'DayOfYear 366'
as '1970' is not a leap year"]},"productionError":null} (processing.8410396611309328049.Project:44)
         */

        System.out.println(new StringToTimestampIso8601().doit("1996-12-31T23:59:59.999"));
        System.out.println(new StringToTimestampIso8601().doit("1996-12-31T23:59:59.000000000000"));
    }
}