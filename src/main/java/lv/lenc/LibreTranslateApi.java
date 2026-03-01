package lv.lenc;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Headers;
import retrofit2.http.POST;

import java.util.List;
import java.util.Map;

public interface LibreTranslateApi {
    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("translate")
    Call<TranslateResponse> translate(@Body TranslateRequest request);

    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("translate")
    Call<ResponseBody> translateRaw(@Body TranslateRequest request);

    // batch: q = [ "a", "b", ... ] -> [ "перевод a", "перевод b", ... ]
    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("translate")
    Call<List<String>> translateBatch(@Body Map<String, Object> body);

    // универсальный «на всякий»
    @Headers({"Content-Type: application/json", "Accept: application/json"})
    @POST("translate")
    Call<com.google.gson.JsonElement> translateAny(@Body Map<String, Object> body);

}

