package org.saiku.olap.discover;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;

import junit.framework.TestCase;

import org.apache.commons.io.IOUtils;
import org.apache.commons.vfs.FileObject;
import org.apache.commons.vfs.FileSystemManager;
import org.apache.commons.vfs.VFS;
import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Assert;
import org.olap4j.Axis;
import org.olap4j.metadata.Cube;
import org.olap4j.metadata.Measure;
import org.saiku.TestSaikuContext;
import org.saiku.olap.dto.SaikuCube;
import org.saiku.olap.query2.ThinQuery;
import org.saiku.olap.query2.util.Fat;
import org.saiku.olap.query2.util.Thin;
import org.saiku.query.Query;
import org.saiku.query.QueryAxis;
import org.saiku.query.QueryHierarchy;
import org.saiku.query.SortOrder;
import org.saiku.query.mdx.NFilter;
import org.saiku.query.mdx.IFilterFunction.MdxFunctionType;
import org.saiku.query.metadata.CalculatedMeasure;
import org.saiku.service.datasource.DatasourceService;
import org.saiku.service.olap.OlapDiscoverService;
import org.saiku.service.olap.ThinQueryService;


public class ThinQueryServiceTest extends TestCase {

	
	private TestSaikuContext context;
    private OlapDiscoverService ods;
    private DatasourceService ds;
    private ThinQueryService tqs;
    
    
    @Override
    protected void setUp() throws Exception {
    	super.setUp();
    	context = TestSaikuContext.instance();
    	ods = context.olapDiscoverService;
    	ds = context.datasourceService;
    	tqs = context.thinQueryService;
    }
    
    
    public void testNewQuery() {
    	try {
    	SaikuCube c = TestSaikuContext.getSalesCube();
    	String name = "dummy";
    	ThinQuery tq = tqs.createEmpty(name, c);
    	ObjectMapper om = new ObjectMapper();
    	String query = om.defaultPrettyPrintingWriter().writeValueAsString(tq);
    	compareQuery(name, query);
       	} catch (Exception e) {
    		e.printStackTrace();
    		Assert.fail();
    	}
    }
    
    public void testQuery1() {
    	
    	try {
    	SaikuCube c = TestSaikuContext.getSalesCube();
		Cube cub = ods.getNativeCube(c);
		String name = "query1";
		Query query = new Query(name, cub);
		QueryAxis columns = query.getAxis(Axis.COLUMNS);
		QueryAxis rows = query.getAxis(Axis.ROWS);
		QueryHierarchy products = query.getHierarchy("Product");

		products.includeLevel("Product Family");
		products.excludeMember("[Product].[Non-Consumable]");
		NFilter top2filter = new NFilter(MdxFunctionType.TopCount, 2, "Measures.[Unit Sales]");
		products.addFilter(top2filter);
		columns.addHierarchy(products);

		QueryHierarchy edu = query.getHierarchy("Education Level");
		edu.includeLevel("Education Level");
		columns.addHierarchy(edu);

		QueryHierarchy gender = query.getHierarchy("Gender");
		gender.includeMember("[Gender].[F]");
		rows.addHierarchy(gender);
		rows.sort(SortOrder.DESC);

		CalculatedMeasure cm =
				query.createCalculatedMeasure(
						"Double Profit", 
						"( [Measures].[Store Sales] - [Measures].[Store Cost]) * 2",  
						null);


		query.getDetails().add(cm);
		Measure m = cub.getMeasures().get(0);

		query.getDetails().add(m);

		ThinQuery tq = Thin.convert(query, c);
		
		ObjectMapper om = new ObjectMapper();
		String first = om.defaultPrettyPrintingWriter().writeValueAsString(tq);
		
		Query q2 = Fat.convert(tq, cub);
		ThinQuery tq2 = Thin.convert(q2, c);
		String second = om.defaultPrettyPrintingWriter().writeValueAsString(tq2);
		assertEquals(first, second);
		
		compareQuery(name, second);
		
//		File f = new File("/tmp/" + name + ".json");
//		FileWriter fw = new FileWriter(f);
//		fw.write(second);
//		fw.flush();
//		fw.close();
		
    	} catch (Exception e) {
    		e.printStackTrace();
    		Assert.fail();
    	}

    	
    }
    
    private void compareQuery(String name, String actual) throws FileNotFoundException, IOException {
    	StringWriter stringWriter = new StringWriter();
    	FileSystemManager fileSystemManager = VFS.getManager();
		FileObject fileObject = fileSystemManager.resolveFile("res:queries/" + name + ".json");
    	IOUtils.copy(fileObject.getContent().getInputStream(), stringWriter);
    	String expected = stringWriter.toString();
    	assertEquals(expected, actual);
    }
    
}
