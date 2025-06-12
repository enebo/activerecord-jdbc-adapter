/***** BEGIN LICENSE BLOCK *****
 * Copyright (c) 2012-2013 Karol Bucek <self@kares.org>
 * Copyright (c) 2006-2010 Nick Sieger <nick@nicksieger.com>
 * Copyright (c) 2006-2007 Ola Bini <ola.bini@gmail.com>
 * Copyright (c) 2008-2009 Thomas E Enebo <enebo@acm.org>
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 ***** END LICENSE BLOCK *****/
package arjdbc.mssql;

import arjdbc.jdbc.Callable;
import arjdbc.jdbc.RubyJdbcConnection;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import arjdbc.util.DateTimeUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.jruby.Ruby;
import org.jruby.RubyArray;
import org.jruby.RubyBoolean;
import org.jruby.RubyClass;
import org.jruby.RubyString;
import org.jruby.RubyTime;
import org.jruby.anno.JRubyMethod;
import org.jruby.runtime.ObjectAllocator;
import org.jruby.runtime.ThreadContext;
import org.jruby.runtime.builtin.IRubyObject;
import org.jruby.util.ByteList;

import static org.jruby.api.Create.newArray;

/**
 *
 * @author nicksieger
 */
public class MSSQLRubyJdbcConnection extends RubyJdbcConnection {
    private static final long serialVersionUID = -745716565005219263L;

    private static final int DATETIMEOFFSET_TYPE;
    private static final Method DateTimeOffsetGetMinutesOffsetMethod;
    private static final Method DateTimeOffsetGetTimestampMethod;
    private static final Method DateTimeOffsetValueOfMethod;
    private static final Method PreparedStatementSetDateTimeOffsetMethod;

    private static final Map<String, Integer> MSSQL_JDBC_TYPE_FOR = new HashMap<String, Integer>(32, 1);
    static {

        Class<?> DateTimeOffset;
        Class<?> MssqlPreparedStatement;
        Class<?> MssqlTypes;
        try {
            DateTimeOffset = Class.forName("microsoft.sql.DateTimeOffset");
            MssqlPreparedStatement = Class.forName("com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement");
            MssqlTypes = Class.forName("microsoft.sql.Types");
        } catch (ClassNotFoundException e) {
            System.err.println("You must require the Microsoft JDBC driver to use this gem"); // The exception doesn't bubble when ruby is initializing
            throw new RuntimeException("You must require the Microsoft JDBC driver to use this gem");
        }

        try {
            DATETIMEOFFSET_TYPE = MssqlTypes.getField("DATETIMEOFFSET").getInt(null);
            DateTimeOffsetGetMinutesOffsetMethod = DateTimeOffset.getDeclaredMethod("getMinutesOffset");
            DateTimeOffsetGetTimestampMethod = DateTimeOffset.getDeclaredMethod("getTimestamp");

            Class<?>[] valueOfArgTypes = { Timestamp.class, int.class };
            DateTimeOffsetValueOfMethod = DateTimeOffset.getDeclaredMethod("valueOf", valueOfArgTypes);

            Class<?>[] setOffsetArgTypes = { int.class, DateTimeOffset };
            PreparedStatementSetDateTimeOffsetMethod = MssqlPreparedStatement.getDeclaredMethod("setDateTimeOffset", setOffsetArgTypes);
        } catch (Exception e) {
            System.err.println("You must require the Microsoft JDBC driver to use this gem"); // The exception doesn't bubble when ruby is initializing
            throw new RuntimeException("Please make sure you are using the latest version of the Microsoft JDBC driver");
        }

        MSSQL_JDBC_TYPE_FOR.put("binary_basic", Types.BINARY);
        MSSQL_JDBC_TYPE_FOR.put("datetimeoffset", DATETIMEOFFSET_TYPE);
        MSSQL_JDBC_TYPE_FOR.put("money", Types.DECIMAL);
        MSSQL_JDBC_TYPE_FOR.put("smalldatetime", Types.TIMESTAMP);
        MSSQL_JDBC_TYPE_FOR.put("smallmoney", Types.DECIMAL);
        MSSQL_JDBC_TYPE_FOR.put("ss_timestamp", Types.BINARY);
        MSSQL_JDBC_TYPE_FOR.put("text_basic", Types.LONGVARCHAR);
        MSSQL_JDBC_TYPE_FOR.put("uuid", Types.CHAR);
        MSSQL_JDBC_TYPE_FOR.put("varchar_max", Types.VARCHAR);
    }

    public MSSQLRubyJdbcConnection(Ruby runtime, RubyClass metaClass) {
        super(runtime, metaClass);
    }

    public static RubyClass createMSSQLJdbcConnectionClass(ThreadContext context, RubyClass jdbcConnection) {
        RubyClass clazz = getConnectionAdapters(context). // ActiveRecord::ConnectionAdapters
                defineClassUnder(context, "MSSQLJdbcConnection", jdbcConnection, ALLOCATOR).
                defineMethods(context, MSSQLRubyJdbcConnection.class);
        getConnectionAdapters(context).setConstant(context, "MssqlJdbcConnection", clazz);

        return clazz;
    }

    public static RubyClass load(final Ruby runtime) {
        var context = runtime.getCurrentContext();
        RubyClass jdbcConnection = getJdbcConnection(context);
        return createMSSQLJdbcConnectionClass(context, jdbcConnection);
    }

    protected static final ObjectAllocator ALLOCATOR = new ObjectAllocator() {
        public IRubyObject allocate(Ruby runtime, RubyClass klass) {
            return new MSSQLRubyJdbcConnection(runtime, klass);
        }
    };

    private static final byte[] EXEC = new byte[] { 'e', 'x', 'e', 'c' };

    @JRubyMethod(name = "exec?", required = 1, meta = true, frame = false)
    public static RubyBoolean exec_p(ThreadContext context, IRubyObject self, IRubyObject sql) {
        final ByteList sqlBytes = sql.asString().getByteList();
        return context.runtime.newBoolean( startsWithIgnoreCase(sqlBytes, EXEC) );
    }

    // Support multiple result sets for mssql
    @Override
    @JRubyMethod(name = "execute", required = 1)
    public IRubyObject execute(final ThreadContext context, final IRubyObject sql) {
        final String query = sqlString(sql);
        return withConnection(context, new Callable<IRubyObject>() {
            public IRubyObject call(final Connection connection) throws SQLException {
                Statement statement = null;
                try {
                    statement = createStatement(context, connection);

                    // For DBs that do support multiple statements, lets return the last result set
                    // to be consistent with AR
                    boolean hasResultSet = doExecute(statement, query);
                    int updateCount = statement.getUpdateCount();

                    final List<IRubyObject> results = new ArrayList<IRubyObject>();
                    ResultSet resultSet;

                    while (hasResultSet || updateCount != -1) {

                        if (hasResultSet) {
                            resultSet = statement.getResultSet();

                            // Unfortunately the result set gets closed when getMoreResults()
                            // is called, so we have to process the result sets as we get them
                            // this shouldn't be an issue in most cases since we're only getting 1 result set anyways
                            results.add(mapExecuteResult(context, connection, resultSet));
                        } else {
                            results.add(context.runtime.newFixnum(updateCount));
                        }

                        // Check to see if there is another result set
                        hasResultSet = statement.getMoreResults();
                        updateCount = statement.getUpdateCount();
                    }

                    if (results.size() == 0) {
                        return context.nil; // If no results, return nil
                    } else if (results.size() == 1) {
                        return results.get(0);
                    } else {
                        return newArray(context, results);
                    }

                } catch (final SQLException e) {
                    debugErrorSQL(context, query);
                    throw e;
                } finally {
                    close(statement);
                }
            }
        });
    }

    /**
     * Executes an INSERT SQL statement
     * @param context
     * @param sql
     * @param pk Rails PK
     * @return ActiveRecord::Result
     * @throws SQLException
     */
    @Override
    @JRubyMethod(name = "execute_insert_pk", required = 2)
    public IRubyObject execute_insert_pk(final ThreadContext context, final IRubyObject sql, final IRubyObject pk) {

        // MSSQL does not like composite primary keys here so chop it if there is more than one column
        IRubyObject modifiedPk = pk;

        if (pk instanceof RubyArray) {
            RubyArray ary = (RubyArray) pk;
            if (ary.size() > 0) {
                modifiedPk = ary.eltInternal(0);
            }
        }

        return super.execute_insert_pk(context, sql, modifiedPk);
    }

    /**
     * Executes an INSERT SQL statement using a prepared statement
     * @param context
     * @param sql
     * @param binds RubyArray of values to be bound to the query
     * @param pk Rails PK
     * @return ActiveRecord::Result
     * @throws SQLException
     */
    @Override
    @JRubyMethod(name = "execute_insert_pk", required = 3)
    public IRubyObject execute_insert_pk(final ThreadContext context, final IRubyObject sql, final IRubyObject binds,
                                         final IRubyObject pk) {
        // MSSQL does not like composite primary keys here so chop it if there is more than one column
        IRubyObject modifiedPk = pk;

        if (pk instanceof RubyArray) {
            RubyArray ary = (RubyArray) pk;
            if (ary.size() > 0) {
                modifiedPk = ary.eltInternal(0);
            }
        }

        return super.execute_insert_pk(context, sql, binds, modifiedPk);
    }

    @Override
    protected Integer jdbcTypeFor(final String type) {

        Integer typeValue = MSSQL_JDBC_TYPE_FOR.get(type);

        if ( typeValue != null ) {
            return typeValue;
        }

        return super.jdbcTypeFor(type);
    }

    // Datetimeoffset values also make it into here
    @Override
    protected void setStringParameter(final ThreadContext context, final Connection connection,
            final PreparedStatement statement, final int index, final IRubyObject value,
            final IRubyObject attribute, final int type) throws SQLException {

        // datetimeoffset values also make it in here
        if (type == DATETIMEOFFSET_TYPE) {

            Object dto = convertToDateTimeOffset(context, value);

            try {

                Object[] setStatementArgs = { index, dto };
                PreparedStatementSetDateTimeOffsetMethod.invoke(statement, setStatementArgs);

            } catch (IllegalAccessException e) {
                debugMessage(context.runtime, e.getMessage());
                throw new RuntimeException("Please make sure you are using the latest version of the Microsoft JDBC driver");
            } catch (InvocationTargetException e) {
                debugMessage(context.runtime, e.getMessage());
                throw new RuntimeException("Please make sure you are using the latest version of the Microsoft JDBC driver");
            }

            return;
        }
        super.setStringParameter(context, connection, statement, index, value, attribute, type);
    }

    // We need higher precision than the default for Time objects (which is milliseconds) so we use a DateTimeOffset object
    @Override
    protected void setTimeParameter(final ThreadContext context,
        final Connection connection, final PreparedStatement statement,
        final int index, IRubyObject value,
        final IRubyObject attribute, final int type) throws SQLException {

        statement.setObject(index, convertToDateTimeOffset(context, value), Types.TIME);

    }

    private Object convertToDateTimeOffset(final ThreadContext context, final IRubyObject value) {

        RubyTime time = (RubyTime) value;
        DateTime dt = time.getDateTime();
        Timestamp timestamp = new Timestamp(dt.getMillis());
        timestamp.setNanos(timestamp.getNanos() + (int) time.getNSec());
        int offsetMinutes = dt.getZone().getOffset(dt.getMillis()) / 60000;

        try {

            Object[] dtoArgs = { timestamp, offsetMinutes };
            return DateTimeOffsetValueOfMethod.invoke(null, dtoArgs);

        } catch (IllegalAccessException e) {
            debugMessage(context.runtime, e.getMessage());
            throw new RuntimeException("Please make sure you are using the latest version of the Microsoft JDBC driver");
        } catch (InvocationTargetException e) {
            debugMessage(context.runtime, e.getMessage());
            throw new RuntimeException("Please make sure you are using the latest version of the Microsoft JDBC driver");
        }
    }


    @Override
    protected RubyArray mapTables(final ThreadContext context, final Connection connection,
            final String catalog, final String schemaPattern, final String tablePattern,
            final ResultSet tablesSet) throws SQLException, IllegalStateException {

        final RubyArray tables = newArray(context);

        while ( tablesSet.next() ) {
            String schema = tablesSet.getString(TABLES_TABLE_SCHEM);
            if ( schema != null ) schema = schema.toLowerCase();
            // Under MS-SQL, don't return system tables/views unless explicitly asked for :
            if ( schemaPattern == null &&
                ( "sys".equals(schema) || "information_schema".equals(schema) ) ) {
                continue;
            }
            String name = tablesSet.getString(TABLES_TABLE_NAME);
            if ( name == null ) {
                // NOTE: seems there's a jTDS but when doing getColumns while
                // EXPLAIN is on (e.g. `SET SHOWPLAN_TEXT ON`) not returning
                // correct result set with table info (null NAME, invalid CAT)
                throw new IllegalStateException("got null name while matching table(s): [" +
                    catalog + "." + schemaPattern + "." + tablePattern + "] check " +
                    "if this happened during EXPLAIN (SET SHOWPLAN_TEXT ON) if so please try " +
                    "turning it off using the system property 'arjdbc.mssql.explain_support.disabled=true' " +
                    "or programatically by changing: `ArJdbc::MSSQL::ExplainSupport::DISABLED`");
            }
            tables.add( cachedString(context, caseConvertIdentifierForRails(connection, name)) );
        }
        return tables;
    }

    /**
     * Microsoft SQL 2000+ support schemas
     */
    @Override
    protected boolean databaseSupportsSchemas() {
        return true;
    }

    /**
     * Treat LONGVARCHAR as CLOB on MSSQL for purposes of converting a JDBC value to Ruby.
     * Also handle datetimeoffset values here
     */
    @Override
    protected IRubyObject jdbcToRuby(
        final ThreadContext context, final Ruby runtime,
        final int column, int type, final ResultSet resultSet)
        throws SQLException {

        if (type == DATETIMEOFFSET_TYPE) {

            Object dto = resultSet.getObject(column); // Returns a microsoft.sql.DateTimeOffset

            if (dto == null) return context.nil;

            try {

                int minutes = (int) DateTimeOffsetGetMinutesOffsetMethod.invoke(dto);
                DateTimeZone zone = DateTimeZone.forOffsetHoursMinutes(minutes / 60, minutes % 60);
                Timestamp ts = (Timestamp) DateTimeOffsetGetTimestampMethod.invoke(dto);

                int nanos = ts.getNanos(); // max 999-999-999
                nanos = nanos % 1000000;

                // We have to do this differently than the newTime helper because the Timestamp loses its zone information when passed around
                DateTime dateTime = new DateTime(ts.getTime(), zone);
                return RubyTime.newTime(context.runtime, dateTime, nanos);

            } catch (IllegalAccessException e) {
                debugMessage(runtime, e.getMessage());
                return context.nil;
            } catch (InvocationTargetException e) {
                debugMessage(runtime, e.getMessage());
                return context.nil;
            }
        }

        if (type == Types.LONGVARCHAR || type == Types.LONGNVARCHAR) type = Types.CLOB;
        return super.jdbcToRuby(context, runtime, column, type, resultSet);
    }

    /**
     * Converts a JDBC date object to a Ruby date by referencing Date#civil
     * @param context current thread context
     * @param resultSet the jdbc result set to pull the value from
     * @param index the index of the column to convert
     * @return RubyNil if NULL or RubyDate if there is a value
     * @throws SQLException if it fails to retrieve the value from the result set
     */
    @Override
    protected IRubyObject dateToRuby(ThreadContext context, Ruby runtime, ResultSet resultSet, int index) throws SQLException {

        final Date value = resultSet.getDate(index);

        if (value == null) return context.nil;

        return DateTimeUtils.newDate(context, value);
    }

    /**
     * Converts a JDBC time to a Ruby time. We use timestamp because java.sql.Time doesn't support sub-millisecond values
     * @param context current thread context
     * @param resultSet the jdbc result set to pull the value from
     * @param index the index of the column to convert
     * @return RubyNil if NULL or RubyTime if there is a value
     * @throws SQLException if it fails to retrieve the value from the result set
     */
    @Override
    protected IRubyObject timeToRuby(final ThreadContext context,final Ruby runtime,
            final ResultSet resultSet, final int column) throws SQLException {

        return timestampToRuby(context, runtime, resultSet, column);
    }

    @Override
    protected ColumnData[] extractColumns(final ThreadContext context,
        final Connection connection, final ResultSet resultSet,
        final boolean downCase) throws SQLException {
        return filterRowNumFromColumns( super.extractColumns(context, connection, resultSet, downCase) );
    }

    /**
     * Filter out the <tt>_row_num</tt> column from results.
     */
    private static ColumnData[] filterRowNumFromColumns(final ColumnData[] columns) {
        for ( int i = 0; i < columns.length; i++ ) {
            if ( "_row_num".equals( columns[i].getName() ) ) {
                final ColumnData[] filtered = new ColumnData[columns.length - 1];

                if ( i > 0 ) {
                    System.arraycopy(columns, 0, filtered, 0, i);
                }

                if ( i + 1 < columns.length ) {
                    System.arraycopy(columns, i + 1, filtered, i, columns.length - (i + 1));
                }

                return filtered;
            }
        }
        return columns;
    }

    @Override
    protected void releaseSavepoint(final Connection connection, final Savepoint savepoint) throws SQLException {
        // MSSQL doesn't support releasing savepoints
    }

}
