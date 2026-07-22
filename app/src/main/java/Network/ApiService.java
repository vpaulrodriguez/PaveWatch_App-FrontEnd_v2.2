package Network;

import java.util.List;
import java.util.Map;
import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {

    @GET("api/pavewatchs/mapa-calor")
    Call<List<BacheDTO>> getMapaCalor();

    @GET("api/pavewatchs/estadisticas/por-clasificacion-ia")
    Call<Map<String, Long>> getEstadisticasSeveridad();
}

