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
    public void updateCache(@RequestParam(name = "profile") String profile) {
        cacheService.updateCache(profile);
    }

    @GetMapping("promoteCache")
    public void promoteCache(@RequestParam(name = "profile") String profile) {
        cacheService.promoteCache(profile);
    }

    @GetMapping("dropCache")
    public void dropCache(@RequestParam(name = "profile") String profile) throws Exception {
        cacheService.dropCache(profile);
    }

    @GetMapping("stats")
    public Map<String, Object> getStats() throws Exception {
        return cacheService.getStats();
    }
}