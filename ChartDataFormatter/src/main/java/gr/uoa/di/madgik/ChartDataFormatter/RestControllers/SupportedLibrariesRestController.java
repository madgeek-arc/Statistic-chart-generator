package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

import gr.uoa.di.madgik.ChartDataFormatter.Handlers.SupportedLibraries;

@RestController
public class SupportedLibrariesRestController {

    private static String[] getNames(Class<? extends Enum<?>> e) {
        return Arrays.stream(e.getEnumConstants()).map(Enum::name).toArray(String[]::new);
    }

    @GetMapping( path = "/chart/libraries",
        produces = "application/json; charset=UTF-8")

    public @ResponseBody ResponseEntity getSupportedLibraries(){

        String[] supportedLibraries = getNames(SupportedLibraries.class);
        return new ResponseEntity<>(supportedLibraries, HttpStatus.OK);
    }
}
