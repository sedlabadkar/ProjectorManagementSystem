import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataBase {

    // Logging
    private final Logger appLogger = LoggerFactory.getLogger(DataBase.class);


    // JDBC driver name and database URL
    static final String JDBC_DRIVER = "org.sqlite.JDBC";
    static final String DB_URL = "jdbc:sqlite:db/test.db";

    // Database credentials
    // static final String USER = "username";
    // static final String PASS = "password";

    private Connection conn = null;

     /**
     * Connect to the database.
     */
    // TODO: Because app is hosted locally we may get away with opening a connection on for every request.
    // But definately need to add connection pooling to make it prod ready.
    // http://www.mchange.com/projects/c3p0/#prerequisites
    public void connect() throws ClassNotFoundException {
        Class.forName(JDBC_DRIVER);
        try {
            this.conn = DriverManager.getConnection(DB_URL);
            this.conn.setAutoCommit(true);
            appLogger.info("Connection to SQLite has been established.");
            
        } catch (SQLException e) {
            appLogger.info("SQL Exception " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (this.conn != null) {
                this.conn.close();
            }
        } catch (SQLException e) {
            appLogger.error("SQL Exception " + e.getMessage());
        }
    }

    public void update (String[] updateSqls) throws ClassNotFoundException{
        if(this.conn == null) this.connect();
        try {
            Statement statement = this.conn.createStatement();
            statement.setQueryTimeout(30);

            int i = updateSqls.length;
            while(i > 0) {
                i--;
                statement.executeUpdate(updateSqls[i]);
            }

        } catch(SQLException e) {
            // if the error message is "out of memory",
            // it probably means no database file is found
            appLogger.info("SQL Exception " + e.getMessage());
        }
    }

    public ResultSet query(String sql){
        try {
            if (this.conn == null) this.connect();
            Statement statement = this.conn.createStatement();
            statement.setQueryTimeout(30);
            ResultSet rs = statement.executeQuery(sql);
            return rs;
        } catch(SQLException e) {
            // if the error message is "out of memory",
            // it probably means no database file is found
            appLogger.info("SQL Exception " + e.getMessage());
        } catch (ClassNotFoundException cnfe){
            appLogger.error("Class Not Found Exception. Possibly, the SQLite jdbc drive jar could not be loaded. Check dependencies/Classpath");
        }
        return null;
    }

    private void fillUpProjectors(){
         String[] queries = {
                 "INSERT INTO projectors VALUES (1, \"Projector 1\");",
                 "INSERT INTO projectors VALUES (2, \"Projector 2\");",
                 "INSERT INTO projectors VALUES (3, \"Projector 3\");",
         };
        try {
            this.update(queries);
        } catch (ClassNotFoundException cnfe){
            appLogger.error("Class Not Found Exception. Possibly, the SQLite jdbc drive jar could not be loaded. Check dependencies/Classpath");
        }
    }

    private void fillUpTeams(){
        String[] queries = {
                "INSERT INTO teams VALUES (1, \"Team 1\");",
                "INSERT INTO teams VALUES (2, \"Team 2\");",
                "INSERT INTO teams VALUES (3, \"Team 3\");",
                "INSERT INTO teams VALUES (4, \"Team 4\");",
                "INSERT INTO teams VALUES (5, \"Team 5\");",
        };
        try {
            this.update(queries);
        } catch (ClassNotFoundException cnfe){
            appLogger.error("Class Not Found Exception. Possibly, the SQLite jdbc drive jar could not be loaded. Check dependencies/Classpath");
        }
    }

    public void createSchema() {
        // TODO: Handle migrations properly
        String createProjectorsTable = "CREATE TABLE IF NOT EXISTS projectors " +
                    "(id INTEGER not NULL, " +
                    " name VARCHAR(255), " +
                    " PRIMARY KEY ( id ))";

        String timeSlotsTable = "CREATE TABLE IF NOT EXISTS time_slots " +
                    "(id INTEGER not NULL, " +
                    " start INTEGER, " +
                    " duration INTEGER, " +
                    " recur_every INTEGER, " +
                    " end INTEGER, " +
                    " PRIMARY KEY ( id ))";

        String createTeamsTable = "CREATE TABLE IF NOT EXISTS teams " +
                "(id INTEGER not NULL, " +
                " name VARCHAR(255), " +
                " PRIMARY KEY ( id ))";

        String createAllocationsTable = "CREATE TABLE IF NOT EXISTS allocations " +
                    "(id INTEGER not NULL, " +
                    " projector_id INTEGER not NULL, " +
                    " time_slot_id INTEGER not NULL, " +
                    " team_id INTEGER not NULL, " +
                    " PRIMARY KEY ( id ))";

        String[] prepareSchemaStatements = {
            createProjectorsTable,
            timeSlotsTable,
            createTeamsTable,
            createAllocationsTable
        };
        try {
            this.update(prepareSchemaStatements);
            fillUpProjectors();
            fillUpTeams();
        } catch (ClassNotFoundException cnfe){
            appLogger.error("Class Not Found Exception. Possibly, the SQLite jdbc drive jar could not be loaded. Check dependencies/Classpath");
        }
        //this.close();
    }

    private static DataBase instance = null;
    private DataBase() {}
    public static DataBase getInstance() {
        if(instance == null) {
            instance = new DataBase();
        }
        return instance;
    }
}
