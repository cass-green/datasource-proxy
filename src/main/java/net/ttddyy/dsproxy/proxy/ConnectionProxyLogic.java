package net.ttddyy.dsproxy.proxy;

import net.ttddyy.dsproxy.ConnectionInfo;
import net.ttddyy.dsproxy.listener.MethodExecutionListenerUtils;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import net.ttddyy.dsproxy.transform.TransformInfo;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;

/**
 * Proxy Logic implementation for {@link Connection} methods.
 *
 * @author Tadaya Tsuyukubo
 * @since 1.2
 */
public class ConnectionProxyLogic extends CallbackSupport {

    private Connection connection;
    private ConnectionInfo connectionInfo;
    private ProxyConfig proxyConfig;

    public ConnectionProxyLogic(Connection connection, ConnectionInfo connectionInfo, ProxyConfig proxyConfig) {
        this.connection = connection;
        this.connectionInfo = connectionInfo;
        this.proxyConfig = proxyConfig;
    }

    public Object invoke(Object proxyConnection, Method method, Object[] args) throws Throwable {

        boolean isCloseMethod = "close".equals(method.getName());
        boolean isCommitMethod = "commit".equals(method.getName());
        boolean isRollbackMethod = "rollback".equals(method.getName());

        return MethodExecutionListenerUtils.invoke(
                (proxyTarget, targetMethod, targetArgs) -> {
                    Object result = performQueryExecutionListener(proxyConnection, targetMethod, targetArgs);
                    ConnectionInfo connectionInfo = ConnectionProxyLogic.this.connectionInfo;
                    if (isCommitMethod) {
                        connectionInfo.incrementCommitCount();
                    } else if (isRollbackMethod) {
                        connectionInfo.incrementRollbackCount();
                    } else if (isCloseMethod) {
                        connectionInfo.setClosed(true);
                        String connId = connectionInfo.getConnectionId();
                        ConnectionProxyLogic.this.proxyConfig.getConnectionIdManager().addClosedId(connId);
                    }
                    return result;
                }, this.proxyConfig, this.connection, this.connectionInfo, method, args);
    }

    private Object performQueryExecutionListener(Object proxy, Method method, Object[] args) throws Throwable {
        final Connection proxyConnection = (Connection) proxy;
        final String methodName = method.getName();

        QueryTransformer queryTransformer = this.proxyConfig.getQueryTransformer();
        JdbcProxyFactory jdbcProxyFactory = this.proxyConfig.getJdbcProxyFactory();


        if ("toString".equals(methodName)) {
            final StringBuilder sb = new StringBuilder();
            sb.append(this.connection.getClass().getSimpleName());
            sb.append(" [");
            sb.append(this.connection.toString());
            sb.append("]");
            return sb.toString(); // differentiate toString message.
        } else if ("getDataSourceName".equals(methodName)) {
            return this.connectionInfo.getDataSourceName();
        } else if ("getTarget".equals(methodName)) {
            // ProxyJdbcObject interface has method to return original object.
            return this.connection;
        }

        if (isWrapperMethods(methodName)) {
            return handleWrapperMethods(methodName, this.connection, args);
        }

        // replace query for PreparedStatement and CallableStatement
        if ("prepareStatement".equals(methodName) || "prepareCall".equals(methodName)) {
            if (ObjectArrayUtils.isFirstArgString(args)) {
                final String query = (String) args[0];
                final Class<? extends Statement> clazz =
                        "prepareStatement".equals(methodName) ? PreparedStatement.class : CallableStatement.class;
                final TransformInfo transformInfo = new TransformInfo(clazz, this.connectionInfo.getDataSourceName(), query, false, 0);
                final String transformedQuery = queryTransformer.transformQuery(transformInfo);
                args[0] = transformedQuery;
            }
        }

        // Invoke method on original Connection.
        final Object retVal;
        try {
            retVal = method.invoke(this.connection, args);
        } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
        }

        // when it is a call to createStatement, prepareStatement or prepareCall, returns a proxy.
        // most of the time, spring and hibernate use prepareStatement to execute query as batch
        if ("createStatement".equals(methodName)) {
            // for normal statement, transforming query is handled inside of handler.
            return jdbcProxyFactory.createStatement((Statement) retVal, this.connectionInfo, proxyConnection, this.proxyConfig);
        } else if ("prepareStatement".equals(methodName)) {
            if (ObjectArrayUtils.isFirstArgString(args)) {
                final String query = (String) args[0];

                // check auto-generated-keys is enabled for these methods:
                //   prepareStatement(String,int), prepareStatement(String,int[]), prepareStatement(String,String[])
                final boolean generateKey = GeneratedKeysUtils.isAutoGenerateEnabledParameters(args);

                return jdbcProxyFactory.createPreparedStatement((PreparedStatement) retVal, query,
                        this.connectionInfo, proxyConnection, this.proxyConfig, generateKey);
            }
        } else if ("prepareCall".equals(methodName)) {  // for stored procedure call
            if (ObjectArrayUtils.isFirstArgString(args)) {
                final String query = (String) args[0];
                return jdbcProxyFactory.createCallableStatement((CallableStatement) retVal, query,
                        this.connectionInfo, proxyConnection, this.proxyConfig);
            }
        }

        return retVal;
    }

}
