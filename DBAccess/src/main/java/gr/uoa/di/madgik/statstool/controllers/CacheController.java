package gr.uoa.di.madgik.statstool.controllers;

import gr.uoa.di.madgik.statstool.services.CacheService;
import gr.uoa.di.madgik.statstool.services.StatsServiceException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    @GetMapping("dropCache")
    public void dropCache() throws Exception {
        cacheService.dropCache();
    }

    @GetMapping("stats")
    public Map<String, Object> getStats() throws Exception {
        return cacheService.getStats();
    }
}