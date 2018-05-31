package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import gr.uoa.di.madgik.ChartDataFormatter.DataFormatter.SupportedChartTypes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
public class SupportedChartTypesRestController {

    private static String[] getNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }

    @GetMapping( path = "/chart/types",
        produces = "application/json; charset=UTF-8")

    public @ResponseBody
    ResponseEntity getSupportedLibraries(){

        String[] supportedTypes = getNames(SupportedChartTypes.class);
        return new ResponseEntity<>(supportedTypes, HttpStatus.OK);
    }
}
