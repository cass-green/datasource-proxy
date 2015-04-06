package net.ttddyy.dsproxy.support;

import net.ttddyy.dsproxy.QueryCount;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Tadaya Tsuyukubo
 * @since 1.3
 */
public class DefaultQueryCountLogFormatterTest {

    @Test
    public void logFormat() {
        QueryCount queryCount = new QueryCount();
        queryCount.setTime(1);
        queryCount.setTotal(2);
        queryCount.setSuccess(3);
        queryCount.setFailure(4);
        queryCount.setSelect(5);
        queryCount.setInsert(6);
        queryCount.setUpdate(7);
        queryCount.setDelete(8);
        queryCount.setOther(9);

        String log = new DefaultQueryCountLogFormatter().getLogMessage("DS", queryCount);
        assertThat(log).isEqualTo("Name:\"DS\", Time:1, Total:2, Success:3, Failure:4, Select:5, Insert:6, Update:7, Delete:8, Other:9");

        String jsonLog = new DefaultQueryCountLogFormatter().getLogMessageAsJson("DS", queryCount);
        assertThat(jsonLog).isEqualTo("{\"name\":\"DS\", \"time\":1, \"total\":2, \"success\":3, \"failure\":4, \"select\":5, \"insert\":6, \"update\":7, \"delete\":8, \"other\":9}");
    }
    @Test
    public void logFormatWithNoName() {
        QueryCount queryCount = new QueryCount();

        String log = new DefaultQueryCountLogFormatter().getLogMessage(null, queryCount);
        assertThat(log).isEqualTo("Name:\"\", Time:0, Total:0, Success:0, Failure:0, Select:0, Insert:0, Update:0, Delete:0, Other:0");

        String jsonLog = new DefaultQueryCountLogFormatter().getLogMessageAsJson(null, queryCount);
        assertThat(jsonLog).isEqualTo("{\"name\":null, \"time\":0, \"total\":0, \"success\":0, \"failure\":0, \"select\":0, \"insert\":0, \"update\":0, \"delete\":0, \"other\":0}");
    }
}
