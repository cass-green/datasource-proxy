package net.ttddyy.dsproxy.listener;

import net.ttddyy.dsproxy.ExecutionInfo;
import net.ttddyy.dsproxy.QueryInfo;
import net.ttddyy.dsproxy.proxy.ParameterSetOperation;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.List;

/**
 * In addition to {@link DefaultQueryLogEntryCreator}, append output parameter values to the log for {@link CallableStatement}.
 *
 * @author Parikshit Navgire (navgire@optymyze.com)
 * @author Tadaya Tsuyukubo
 * @since 1.3.2
 */
public class OracleOutputParameterLogEntryCreator extends DefaultQueryLogEntryCreator {

    @Override
    public String getLogEntry(ExecutionInfo execInfo, List<QueryInfo> queryInfoList, boolean writeDataSourceName) {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.getLogEntry(execInfo, queryInfoList, writeDataSourceName));

        sb.append(", OutParams:[");

        for (QueryInfo queryInfo : queryInfoList) {
            for (List<ParameterSetOperation> parameters : queryInfo.getParametersList()) {
                sb.append("(");
                if (hasOutputParameters(parameters)) {
                    String str = getOutputParameters(parameters, (CallableStatement) execInfo.getStatement(), false);
                    sb.append(str);
                }
                sb.append("),");
            }
        }

        chompIfEndWith(sb, ',');
        sb.append("]");
        return sb.toString();
    }

    @Override
    public String getLogEntryAsJson(ExecutionInfo execInfo, List<QueryInfo> queryInfoList, boolean writeDataSourceName) {
        final StringBuilder sb = new StringBuilder();
        sb.append(super.getLogEntryAsJson(execInfo, queryInfoList, writeDataSourceName));

        chompIfEndWith(sb, '}');  // hack to remove closing curly bracket from returned json string

        sb.append(",\"outParams\":[");


        for (QueryInfo queryInfo : queryInfoList) {
            for (List<ParameterSetOperation> parameters : queryInfo.getParametersList()) {
                sb.append("{");
                if (hasOutputParameters(parameters)) {
                    String str = getOutputParameters(parameters, (CallableStatement) execInfo.getStatement(), true);
                    sb.append(str);
                }
                sb.append("},");
            }
        }

        chompIfEndWith(sb, ',');
        sb.append("]");
        sb.append("}");

        return sb.toString();
    }


    private String getOutputParameters(List<ParameterSetOperation> params, CallableStatement st, boolean isJson) {

        StringBuilder sb = new StringBuilder();
        for (ParameterSetOperation param : params) {
            if (!ParameterSetOperation.isRegisterOutParameterOperation(param)) {
                continue;
            }

            Object key = param.getArgs()[0];
            Object value = getOutputValueForDisplay(key, st);

            if (isJson) {
                sb.append("\"");
                sb.append(escapeSpecialCharacterForJson(key.toString()));
                sb.append("\":");

                if (value == null) {
                    sb.append("null");
                } else {
                    sb.append("\"");
                    sb.append(value);
                    sb.append("\"");
                }
                sb.append(",");

            } else {
                sb.append(key);
                sb.append("=");
                sb.append(value);
                sb.append(",");
            }

        }
        chompIfEndWith(sb, ',');

        return sb.toString();
    }

    protected Object getOutputValueForDisplay(Object key, CallableStatement cs) {
        Object value;
        try {
            if (key instanceof String) {
                value = cs.getObject((String) key);  // access by name
            } else {
                value = cs.getObject((Integer) key);  // access by index
            }
        } catch (SQLException e) {
            return "[FAILED TO RETRIEVE]";
        }
        return value;
    }

    private boolean hasOutputParameters(List<ParameterSetOperation> params) {
        for (ParameterSetOperation param : params) {
            if (ParameterSetOperation.isRegisterOutParameterOperation(param)) {
                return true;
            }
        }
        return false;
    }


}
