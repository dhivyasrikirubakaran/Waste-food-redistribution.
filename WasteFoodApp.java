
import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
/**
 * WasteFoodApp - Console prototype for waste food redistribution
 * Save as WasteFoodApp.java
 *
 * Simple CSV persistence:
 * - donations.csv
 * - users.csv
 *
 * Roles: donor, ngo, volunteer, admin
 */
public class WasteFoodApp {
    private static final String DONATION_FILE = "donations.csv";
    private static final String USER_FILE = "users.csv";
    private static final Scanner scanner = new Scanner(System.in);
    private static final DateTimeFormatter dtf = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private final List<Donation> donations = new ArrayList<>();
    private final List<User> users = new ArrayList<>();

    public static void main(String[] args) {
        WasteFoodApp app = new WasteFoodApp();
        app.loadAll();
        app.seedSampleDataIfEmpty();
        app.mainMenu();
        app.saveAll();
        System.out.println("Exiting. Goodbye!");
    }  private void mainMenu() {
        while (true) {
            System.out.println("\n=== Waste Food Redistribution App ===");
            System.out.println("1. Register user");
            System.out.println("2. Login as user");
            System.out.println("3. List donations (public)");
            System.out.println("4. Admin reports");
            System.out.println("0. Exit");
            System.out.print("Choose: ");
            String ch = scanner.nextLine().trim();
            switch (ch) {
                case "1": registerUser(); break;
                case "2": loginUser(); break;
                case "3": listAvailableDonations(); break;
                case "4": adminReports(); break;
                case "0": return;
                default: System.out.println("Invalid choice.");
            }
        }
    } private void registerUser() {
        System.out.print("Enter name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Role (donor/ngo/volunteer/admin): ");
        String role = scanner.nextLine().trim().toLowerCase();
        System.out.print("City: ");
        String city = scanner.nextLine().trim();
        User u = new User(UUID.randomUUID().toString(), name, role, city);
        users.add(u);
        saveUsers(); // immediate save
        System.out.println("Registered: " + u);
    }  private void loginUser() {
        System.out.print("Enter your name to login: ");
        String name = scanner.nextLine().trim();
        List<User> found = findUsersByName(name);
        if (found.isEmpty()) {
            System.out.println("No user found with that name. Register first.");
            return;
        }
        User u = found.get(0);
        System.out.println("Welcome, " + u.name + " (" + u.role + ")");
        switch (u.role) {
            case "donor": donorMenu(u); break;
            case "ngo": ngoMenu(u); break;
            case "volunteer": volunteerMenu(u); break;
            case "admin": adminMenu(u); break;
            default: System.out.println("Unknown role.");
        }
    }   private void donorMenu(User u) {
        while (true) {
            System.out.println("\n--- Donor Menu ---");
            System.out.println("1. Create donation");
            System.out.println("2. My donations");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String ch = scanner.nextLine().trim();
            if ("1".equals(ch)) createDonation(u);
            else if ("2".equals(ch)) listDonationsByDonor(u.name);
            else if ("0".equals(ch)) { saveAll(); return; }
            else System.out.println("Invalid.");
        }
    }  private void ngoMenu(User u) {
        while (true) {
            System.out.println("\n--- NGO Menu ---");
            System.out.println("1. View available donations (recommended)");
            System.out.println("2. Claim donation");
            System.out.println("3. My claimed donations");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String ch = scanner.nextLine().trim();
            if ("1".equals(ch)) viewRecommendedForUser(u);
            else if ("2".equals(ch)) claimDonation(u);
            else if ("3".equals(ch)) listDonationsClaimedBy(u.name);
            else if ("0".equals(ch)) { saveAll(); return; }
            else System.out.println("Invalid.");
        }
    }private void volunteerMenu(User u) {
        // volunteers have same capabilities as NGOs for prototype
        ngoMenu(u);
    }private void adminMenu(User u) {
        while (true) {
            System.out.println("\n--- Admin Menu ---");
            System.out.println("1. View all donations");
            System.out.println("2. Force mark donation delivered");
            System.out.println("0. Logout");
            System.out.print("Choose: ");
            String ch = scanner.nextLine().trim();
            if ("1".equals(ch)) listAllDonations();
            else if ("2".equals(ch)) markDeliveredForce();
            else if ("0".equals(ch)) { saveAll(); return; }
            else System.out.println("Invalid.");
        }
    }
    private void createDonation(User donor) {
        System.out.println("Creating donation (donor: " + donor.name + ")");
        System.out.print("Food description: ");
        String desc = scanner.nextLine().trim();
        System.out.print("Quantity (approx, e.g., 10 meals): ");
        String qty = scanner.nextLine().trim();
        System.out.println("Perishability (1-Nonperishable, 2-Short-life, 3-Immediate)");
        System.out.print("Choice: ");
        String pch = scanner.nextLine().trim();
        int per = 1;
        try { per = Integer.parseInt(pch); } catch (Exception e) { per = 1; }
        String perStr = per==3 ? "Immediate" : per==2 ? "Short-life" : "Nonperishable";
        System.out.print("City: ");
        String city = scanner.nextLine().trim();
        System.out.print("Pickup by (YYYY-MM-DDTHH:MM, e.g., 2025-10-28T14:00): ");
        String pickup = scanner.nextLine().trim();
        LocalDateTime pickupBy;
        try { pickupBy = LocalDateTime.parse(pickup); } catch (Exception e) {
            pickupBy = LocalDateTime.now().plusHours(6);
            System.out.println("Invalid format. Defaulting pickupBy to " + pickupBy.format(dtf));
        }
        Donation d = new Donation(UUID.randomUUID().toString(), donor.name, desc, qty, perStr, city, pickupBy, "AVAILABLE", null, LocalDateTime.now());
        donations.add(d);
        saveDonations();
        System.out.println("Donation created: " + d.id);
    }
 private void viewRecommendedForUser(User user) {
        System.out.println("Recommended donations for city: " + user.city);
        List<Donation> available = getAvailableDonations();
        // ranking
        available.sort(Comparator.comparingInt(Donation::perishabilityRank).reversed()
                .thenComparing(d -> d.pickupBy));
        // show those in same city first
        int shown = 0;
        for (Donation d : available) {
            if (d.city.equalsIgnoreCase(user.city)) {
                printDonationSummary(d);
                shown++;
            }
        }
        if (shown==0) {
            System.out.println("No immediate matches in your city. Showing other available donations:");
            for (Donation d : available) {
                printDonationSummary(d);
            }
        }
    }
 private void claimDonation(User user) {
        System.out.print("Enter donation id to claim: ");
        String id = scanner.nextLine().trim();
        Donation d = findDonationById(id);
        if (d == null) { System.out.println("Not found."); return; }
        if (!"AVAILABLE".equals(d.status)) { System.out.println("Not available. Status: " + d.status); return; }
        d.status = "CLAIMED";
        d.claimedBy = user.name;
        saveDonations();
        System.out.println("Claimed donation " + d.id + " by " + user.name);
        System.out.println("Please coordinate pickup before " + d.pickupBy.format(dtf));
    }
 private void listDonationsClaimedBy(String name) {
        System.out.println("Claims by " + name);
        donations.stream()
                .filter(d -> name.equalsIgnoreCase(d.claimedBy))
                .forEach(this::printDonationFull);
   }
    private void adminReports() {
        System.out.println("--- Admin Reports ---");
        long total = donations.size();
        long available = donations.stream().filter(d -> "AVAILABLE".equals(d.status)).count();
        long claimed = donations.stream().filter(d -> "CLAIMED".equals(d.status)).count();
        long delivered = donations.stream().filter(d -> "DELIVERED".equals(d.status)).count();
        System.out.println("Total donations: " + total);
        System.out.println("Available: " + available + ", Claimed: " + claimed + ", Delivered: " + delivered);
    }
 private void listAvailableDonations() {
        System.out.println("Available donations:");
        getAvailableDonations().forEach(this::printDonationSummary);
    }
  private void listAllDonations() {
        donations.forEach(this::printDonationFull);
    }
  private void markDeliveredForce() {
        System.out.print("Enter donation id to mark delivered: ");
        String id = scanner.nextLine().trim();
        Donation d = findDonationById(id);
        if (d == null) { System.out.println("Not found."); return; }
        d.status = "DELIVERED";
        saveDonations();
        System.out.println("Marked delivered: " + d.id);
    }
  private void loadAll() {
        loadUsers();
        loadDonations();
    }
    private void saveAll() {
        saveUsers();
        saveDonations();
    }
   private void loadUsers() {
        users.clear();
        File f = new File(USER_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                // id|name|role|city
                String[] parts = line.split("\\|", -1);
                if (parts.length >= 4) users.add(new User(parts[0], parts[1], parts[2], parts[3]));
            }
        } catch (IOException e) {
            System.out.println("Failed load users: " + e.getMessage());
        }
    }
  private void saveUsers() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(USER_FILE))) {
            for (User u : users) {
                pw.println(String.join("|", u.id, u.name, u.role, u.city));
            }
        } catch (IOException e) {
            System.out.println("Failed save users: " + e.getMessage());
        }
    }
  private void loadDonations() {
        donations.clear();
        File f = new File(DONATION_FILE);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                // id|donor|desc|qty|perish|city|pickupBy|status|claimedBy|createdAt
                String[] p = line.split("\\|", -1);
                if (p.length >= 10) {
                    Donation d = new Donation(p[0], p[1], p[2], p[3], p[4], p[5],
                            LocalDateTime.parse(p[6]), p[7], emptyToNull(p[8]),
                            LocalDateTime.parse(p[9]));
                    donations.add(d);
                }
            }
        } catch (IOException e) {
            System.out.println("Failed load donations: " + e.getMessage());
        }
    }
 private void saveDonations() {
        try (PrintWriter pw = new PrintWriter(new FileWriter(DONATION_FILE))) {
            for (Donation d : donations) {
                pw.println(String.join("|",
                        d.id, d.donorName, escape(d.foodDescription), d.quantity, d.perishability,
                        d.city, d.pickupBy.format(dtf), d.status, d.claimedBy==null ? "" : d.claimedBy,
                        d.createdAt.format(dtf)
                ));
            }
        } catch (IOException e) {
            System.out.println("Failed save donations: " + e.getMessage());
        }
    }
 private void seedSampleDataIfEmpty() {
        if (!donations.isEmpty() || !users.isEmpty()) return;
        // sample users
        users.add(new User("u1", "Ravi", "donor", "Chennai"));
        users.add(new User("u2", "SakhiNGO", "ngo", "Chennai"));
        users.add(new User("u3", "Anita", "volunteer", "Bengaluru"));
        users.add(new User("u4", "Admin", "admin", "Mumbai"));
        // sample donations
        donations.add(new Donation("d1", "Ravi", "50 cooked meals (idli & sambar)", "50 meals", "Immediate", "Chennai",
                LocalDateTime.now().plusHours(3), "AVAILABLE", null, LocalDateTime.now().minusHours(1)));
        donations.add(new Donation("d2", "LocalCafe", "Breads & pastries (day old)", "30 pcs", "Short-life", "Chennai",
                LocalDateTime.now().plusDays(1), "AVAILABLE", null, LocalDateTime.now().minusDays(1)));
        donations.add(new Donation("d3", "GroceryStore", "Canned goods", "20 packs", "Nonperishable", "Bengaluru",
                LocalDateTime.now().plusDays(5), "AVAILABLE", null, LocalDateTime.now().minusDays(2)));
        saveAll();
        System.out.println("Seeded sample data.");
    }
   private List<User> findUsersByName(String name) {
        List<User> r = new ArrayList<>();
        for (User u : users) if (u.name.equalsIgnoreCase(name)) r.add(u);
        return r;
    }  private Donation findDonationById(String id) {
        for (Donation d : donations) if (d.id.equals(id)) return d;
        return null;
    }
 private List<Donation> getAvailableDonations() {
        List<Donation> r = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Donation d : donations) {
            if ("AVAILABLE".equals(d.status) && d.pickupBy.isAfter(now.minusMinutes(5))) r.add(d);
        }
        return r;
    }
  private void listDonationsByDonor(String donorName) {
        System.out.println("Donations by " + donorName);
        donations.stream().filter(d -> donorName.equalsIgnoreCase(d.donorName)).forEach(this::printDonationFull);
    }
  private void printDonationSummary(Donation d) {
        System.out.println(String.format("[%s] %s | %s | %s | pickupBy:%s | status:%s",
                d.id, d.foodDescription, d.quantity, d.city, d.pickupBy.format(dtf), d.status));
    }
  private void printDonationFull(Donation d) {
        System.out.println("ID: " + d.id);
        System.out.println("Donor: " + d.donorName + " | City: " + d.city + " | Created: " + d.createdAt.format(dtf));
        System.out.println("Desc: " + d.foodDescription);
        System.out.println("Qty: " + d.quantity + " | Perishability: " + d.perishability);
        System.out.println("Pickup by: " + d.pickupBy.format(dtf) + " | Status: " + d.status + " | ClaimedBy: " + (d.claimedBy==null ? "-" : d.claimedBy));
        System.out.println("----------------------");
    }

    private static String emptyToNull(String s) { return s == null || s.isEmpty() ? null : s; }
    private static String escape(String s) { return s.replace("\n", " ").replace("|", "/"); }
 static class User {
        String id, name, role, city;
        User(String id, String name, String role, String city) { this.id=id; this.name=name; this.role=role; this.city=city; }
        public String toString(){ return name + " ("+role+") - " + city; }
    }
 static class Donation {
        String id, donorName, foodDescription, quantity, perishability, city;
        LocalDateTime pickupBy;
        String status; // AVAILABLE, CLAIMED, DELIVERED
        String claimedBy;
        LocalDateTime createdAt;
        Donation(String id, String donorName, String foodDescription, String quantity, String perishability, String city,
                 LocalDateTime pickupBy, String status, String claimedBy, LocalDateTime createdAt) {
            this.id=id; this.donorName=donorName; this.foodDescription=foodDescription; this.quantity=quantity;
            this.perishability=perishability; this.city=city; this.pickupBy=pickupBy; this.status=status; this.claimedBy=claimedBy; this.createdAt=createdAt;
        }
        int perishabilityRank() {
            switch(perishability.toLowerCase()) {
                case "immediate": return 3;
                case "short-life": return 2;
                default: return 1;
            }
        }
    }
}
