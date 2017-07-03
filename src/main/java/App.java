import static spark.Spark.*;

public class App {
    private App() {
        DataBase database = DataBase.getInstance();
        database.createSchema();
    }

    private void route(){
        new ProjectorController(ProjectorScheduler.getInstance());

        notFound((req, res) -> {
            res.status(404);
            return "Page Not found";
        });
    }

    public static void main(String[] args){
        App app = new App();
        app.route();
    }
}
