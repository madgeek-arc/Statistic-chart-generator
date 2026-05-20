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
    public void updateCache(@RequestParam(name = "profile", required = false) String profile) {
        cacheService.updateCache(profile);
    }

    @GetMapping("promoteCache")
    public void promoteCache(@RequestParam(name = "profile", required = false) String profile) {
        cacheService.promoteCache(profile);
    }

    @GetMapping("dropCache")
    public void dropCache(@RequestParam(name = "profile", required = false) String profile) throws Exception {
        cacheService.dropCache(profile);
    }

    @GetMapping("stats")
    public Map<String, Object> getStats() throws Exception {
        return cacheService.getStats();
    }

    @GetMapping("dropNlCache")
    public void dropNlCache(@RequestParam(name = "profile", required = false) String profile) {
        cacheService.dropNlCache(profile);
    }

    @GetMapping("evictNlCache")
    public void evictNlCache(@RequestParam(name = "profile") String profile,
                             @RequestParam(name = "nl") String canonicalNl) {
        cacheService.evictNlCache(profile, canonicalNl);
    }

    @GetMapping("dropNlOptionsCache")
    public void dropNlOptionsCache(@RequestParam(name = "library", required = false) String library) {
        cacheService.dropNlOptionsCache(library);
    }

    @GetMapping("evictNlOptionsCache")
    public void evictNlOptionsCache(@RequestParam(name = "library") String library,
                                    @RequestParam(name = "desc") String canonicalDescription) {
        cacheService.evictNlOptionsCache(library, canonicalDescription);
    }
}