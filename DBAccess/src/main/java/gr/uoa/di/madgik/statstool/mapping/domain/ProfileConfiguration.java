package gr.uoa.di.madgik.statstool.mapping.domain;

import gr.uoa.di.madgik.statstool.mapping.entities.Entity;
import gr.uoa.di.madgik.statstool.mapping.entities.Field;
import gr.uoa.di.madgik.statstool.mapping.entities.Join;
import gr.uoa.di.madgik.statstool.mapping.entities.Table;

import java.util.HashMap;
import java.util.List;

public class ProfileConfiguration {
    public HashMap<String, Table> tables = new HashMap<>();
    public HashMap<String, Field> fields = new HashMap<>();
    public HashMap<String, List<Join>> relations = new HashMap<>();

    public HashMap<String, Entity> entities = new HashMap<>();
}
