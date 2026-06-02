import java.sql.*;
public class CheckSeq {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/hms_db?currentSchema=public",
            "hms_user", "hms_pass"
        );
        PreparedStatement stmt = conn.prepareStatement("SELECT document_type, is_activated, prefix_string FROM sequence_generators");
        ResultSet rs = stmt.executeQuery();
        while (rs.next()) {
            System.out.println("DocType: " + rs.getInt("document_type") + 
                ", Active: " + rs.getBoolean("is_activated") + 
                ", Prefix: " + rs.getString("prefix_string"));
        }
        conn.close();
    }
}
