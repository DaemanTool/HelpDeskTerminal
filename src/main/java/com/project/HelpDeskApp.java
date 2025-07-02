package com.project;

// Импорт библиотек и т.п.
import java.sql.*;
import java.util.Properties;
import java.util.Scanner;
import java.util.UUID;

// Создаем основной класс
public class HelpDeskApp {
    // Константы с данными от базы данных
    private static final String DB_URL = "jdbc:postgresql://localhost:5432/postgres";
    private static final String DB_USER = "admin_user";
    private static final String DB_PASSWORD = "K5abba";

    // Основной метод main (Начало тут)
    public static void  main(String[] args) {
        // Пытаемся подключить драйвер Postgre
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            System.err.println("PostgreSQL JDBC Driver не найден!");
            e.printStackTrace();
            return;
        }

        // Создаем соединение и сканер для консоли
        try (Scanner scanner = new Scanner(System.in);
             Connection connection = createConnection()) {
            System.out.println("===== Helpdesk App =====");
            // Бесконечный цикл в меню терминала
            while (true) {

                System.out.println("1. Создать заявку");
                System.out.println("2. Просмотреть заявки");
                System.out.println("3. Обновить статус заявки");
                System.out.println("4. Добавить комментарий");
                System.out.println("5. Выход");
                System.out.print("Выберите действие: ");

                // Сканируем следующий int
                int choice = scanner.nextInt();
                scanner.nextLine(); // очистка буфера

                // Реализация выбора меню
                switch (choice) {
                    case 1:
                        createTicket(connection, scanner);
                        break;
                    case 2:
                        viewTickets(connection);
                        break;
                    case 3:
                        //updateTicketStatus(connection, scanner);
                        break;
                    case 4:
                        addComment(connection, scanner);
                        break;
                    case 5:
                        System.out.println("Выход из приложения...");
                        return;
                    default:
                        System.out.println("Неверный выбор");
                }
            }
        }
        catch (SQLException e) {
            System.err.println("Ошибка подключения к БД: " + e.getMessage());
        }

    }

    // Подключение к БД
    private static Connection createConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", DB_USER);
        props.setProperty("password", DB_PASSWORD);
        props.setProperty("ssl", "false");
        return DriverManager.getConnection(DB_URL, props);
    }

    // Создать заявление (Аккуратнее, в бд должен быть пользователь и статус "Открыт")
    private static void createTicket(Connection conn, Scanner scanner) throws SQLException {
        System.out.println("\n--- Создание новой заявки ---");

        System.out.print("Введите логин: ");
        String creator = scanner.nextLine();

        System.out.print("Заголовок заявки: ");
        String title = scanner.nextLine();

        System.out.print("Описание проблемы: ");
        String description = scanner.nextLine();

        System.out.print("Приоритет (1-высокий, 2-средний, 3-низкий): ");
        int priority = scanner.nextInt();
        scanner.nextLine(); // очистка буфера

        // Начало транзакции

        // Отключаем автокоммит

        conn.setAutoCommit(false);

        try {
            // Вставка заявки
            String insertTicketSQL = "INSERT INTO tickets (title, description, creator_id, status_id, priority) " +
                    "VALUES (?, ?, (SELECT user_id FROM users WHERE full_name = ?), " +
                    "(SELECT status_id FROM ticket_statuses WHERE name = 'Открыт'), ?)";

            // Реализация SQL запроса через JDBC
            try (PreparedStatement ticketStmt = conn.prepareStatement(insertTicketSQL,
                    Statement.RETURN_GENERATED_KEYS)) {

                //ResultSet rsTime = ticketStmt.executeQuery("SELECT CURRENT_TIMESTAMP");

                ticketStmt.setString(1, title);
                ticketStmt.setString(2, description);
                ticketStmt.setString(3, creator);
                ticketStmt.setInt(4, priority);

                int affectedRows = ticketStmt.executeUpdate();

                if (affectedRows == 0) {
                    throw new SQLException("Не удалось создать заявку");
                }

                // Получаем ID созданной заявки
                try (ResultSet generatedKeys = ticketStmt.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        long ticketId = generatedKeys.getLong(1);
                        System.out.println("Заявка #" + ticketId + " успешно создана!");
                    }
                }
            }

            // Фиксация транзакции
            conn.commit();
        } catch (SQLException e) {
            // Откат транзакции при ошибке
            conn.rollback();
            System.err.println("Ошибка при создании заявки: " + e.getMessage());
        } finally {
            // Восстановление авто-коммита
            conn.setAutoCommit(true);
        }
    }

    // Посмотреть заявления
    private static void viewTickets(Connection conn) throws SQLException {
        System.out.println("\n--- Список заявок ---");

        String selectTicketSQL = "SELECT t.ticket_id, t.title, u.full_name as creator, s.name as status, t.created_at " +
                " FROM tickets t" +
                " JOIN users u ON t.creator_id = u.user_id" +
                " JOIN ticket_statuses s ON t.status_id = s.status_id" +
                " ORDER BY t.created_at";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(selectTicketSQL);)
        {
            System.out.println("ID | Заголовок | Автор | Статус | Дата создания");
            System.out.println("-------------------------------------------------");

            while (rs.next())
            {
                System.out.printf("%d %s %s %s %s%n",
                        rs.getLong("ticket_id"),
                        rs.getString("title"),
                        rs.getString("creator"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at"));
            }
            new Scanner(System.in).nextLine();
        }
    }

    // Добавить комментарий
    private static void addComment(Connection conn, Scanner scanner) throws SQLException {
        System.out.println("\n--- Добавление комментария ---");

        System.out.print("Введите ID заявки: ");
        long ticketId = scanner.nextLong();
        scanner.nextLine(); // очистка буфера

        String userLogin = "";
        // Вспоминаем защиту от кривого ввода
        System.out.print("Введите ваш логин: ");
        try {
            userLogin = scanner.nextLine();
        } catch (Exception e) {
            System.err.println("Ошибка ввода: " + e.getMessage());
        }

        System.out.print("Комментарий: ");
        String comment = scanner.nextLine();

        // Начало транзакции
        conn.setAutoCommit(false);

        String insertCommentSQL = "INSERT INTO ticket_comments (ticket_id, author_id, content) " +
                "VALUES (?, (SELECT user_id FROM users WHERE full_name = ?), ?)";

        try (PreparedStatement stmt = conn.prepareStatement(insertCommentSQL))
        {
            stmt.setLong(1, ticketId);
            stmt.setString(2, userLogin);
            stmt.setString(3, comment);

            int affectedRows = stmt.executeUpdate();

            if (affectedRows == 0) {
                throw new SQLException("Не удалось создать комментарий");
            }

            // Фиксация транзакции
            conn.commit();
        }
    }
}
