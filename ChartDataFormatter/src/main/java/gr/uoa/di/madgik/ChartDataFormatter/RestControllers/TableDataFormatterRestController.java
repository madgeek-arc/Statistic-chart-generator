package gr.uoa.di.madgik.ChartDataFormatter.RestControllers;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

@RestController
@RequestMapping("/table")
@CrossOrigin(methods = {RequestMethod.GET,RequestMethod.POST} , origins = "*")
public class TableDataFormatterRestController {

    @GetMapping
    public ModelAndView content(Model model) {

        return new ModelAndView("table");
    }
}
