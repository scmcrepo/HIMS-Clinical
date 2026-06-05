import java.sql.*;
public class CheckSeq {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection(
            "jdbc:postgresql://localhost:5432/hms_db?currentSchema=public",
            "hms_user", "hms_pass"
        );
        System.out.println("--- PURCHASE ORDERS ---");
        PreparedStatement stmt1 = conn.prepareStatement("SELECT id, sequence_number, supplier_id, order_date, order_status FROM purchase_orders");
        ResultSet rs1 = stmt1.executeQuery();
        while (rs1.next()) {
            System.out.println("ID: " + rs1.getString("id") + ", Seq: " + rs1.getString("sequence_number") + ", Supplier: " + rs1.getString("supplier_id") + ", Date: " + rs1.getString("order_date") + ", Status: " + rs1.getString("order_status"));
        }
        System.out.println("--- PURCHASE RECEIPTS ---");
        PreparedStatement stmt2 = conn.prepareStatement("SELECT id, sequence_number, purchase_order_id, receipt_date, notes FROM purchase_receipts");
        ResultSet rs2 = stmt2.executeQuery();
        while (rs2.next()) {
            System.out.println("ID: " + rs2.getString("id") + ", Seq: " + rs2.getString("sequence_number") + ", PO_ID: " + rs2.getString("purchase_order_id") + ", Date: " + rs2.getString("receipt_date") + ", Notes: " + rs2.getString("notes"));
        }
        conn.close();
    }
}
