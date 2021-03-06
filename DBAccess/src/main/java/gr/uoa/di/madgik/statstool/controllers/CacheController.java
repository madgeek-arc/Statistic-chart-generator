package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.services.CacheService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/cache")
@CrossOrigin(methods = {RequestMethod.GET, RequestMethod.POST} , origins = "*")
public class CacheController {
    @Autowired
    private CacheService cacheService;

    @GetMapping("updateCache")
    public void updateCache() {cacheService.updateCache();}

    @GetMapping("promoteCache")
    public void promoteCache() {cacheService.promoteCache();}
}
