package main;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.PrintStream;
import java.util.Collection;
import java.util.List;
import util.Counter;

import math.DifferentiableFunction;
import math.DifferentiableMatrixFunction;
import math.Minimizer;
import math.Norm1Tanh;
import math.QNMinimizer;

import classify.Accuracy;
import classify.LabeledDatum;
import classify.SoftmaxClassifier;

import rae.FineTunableTheta;
import rae.RAECost;
import rae.RAEFeatureExtractor;

public class RAEBuilder {
	FineTunableTheta InitialTheta;
	RAEFeatureExtractor FeatureExtractor;
	DifferentiableMatrixFunction f;
	
	public RAEBuilder()
	{
		InitialTheta = null;
		FeatureExtractor = null;
		f = new Norm1Tanh();		
	}
	
	public static void main(final String[] args) throws Exception
	{
		RAEBuilder rae = new RAEBuilder();
		
		Arguments params = new Arguments();
		params.parseArguments(args);
		if (params.exitOnReturn)
			return;

		System.out.printf("%d\n%d\n",params.DictionarySize,params.hiddenSize);
		
		if( params.TrainModel ){
			System.out.println("Training the RAE. Model file will be saved in " + params.ModelFile);
			FineTunableTheta tunedTheta = rae.train(params);
			tunedTheta.Dump(params.ModelFile);
			System.out.println("RAE trained. The model file is saved in " + params.ModelFile);
			
			RAEFeatureExtractor fe = new RAEFeatureExtractor(params.EmbeddingSize, tunedTheta, 
					params.AlphaCat, params.Beta, params.CatSize, params.Dataset.Vocab.size(), rae.f);
			SoftmaxClassifier<Double,Integer> classifier = new SoftmaxClassifier<Double,Integer>( );
			
			List<LabeledDatum<Double, Integer>> classifierTrainingData 
											= fe.extractFeaturesIntoArray(params.Dataset.Data);
			
			Accuracy TrainAccuracy = classifier.train(classifierTrainingData);
			System.out.println("Train Accuracy :" + TrainAccuracy.toString());
			classifier.Dump(params.ClassifierFile);
		}
		else{
			System.out.println("Using the trained RAE. Model file retrieved from " + params.ModelFile
					+ "\nNote that this overrides all RAE specific arguments you passed.");
			List<LabeledDatum<Double,Integer>> classifierTestingData = null;
			
			FineTunableTheta tunedTheta = rae.load(params);
			assert tunedTheta.getNumCategories() == params.Dataset.getCatSize();
			
			SoftmaxClassifier<Double,Integer> classifier 
									= new SoftmaxClassifier<Double,Integer>(params.ClassifierFile);
			
			RAEFeatureExtractor fe = new RAEFeatureExtractor(params.EmbeddingSize, tunedTheta, 
					params.AlphaCat, params.Beta, params.CatSize, params.Dataset.Vocab.size(), rae.f);
			
			if(params.Dataset.Data.size() > 0)
			{
				System.err.println("There is training data in the directory.");
				System.err.println("It will be ignored when you are not in the training mode.");
			}

			classifierTestingData = fe.extractFeaturesIntoArray(params.Dataset.TestData);
			
			if (params.featuresOutputFile != null)
			{
				PrintStream out = new PrintStream(params.featuresOutputFile);
				for(LabeledDatum<Double, Integer> data : classifierTestingData)
				{
					Collection<Double> features = data.getFeatures();
					for(Double f : features)
						out.printf("%.8f ", f.doubleValue());
					out.println();
				}
				out.close();
			}
			
			if (params.ProbabilitiesOutputFile != null)
			{
				PrintStream out = new PrintStream(params.ProbabilitiesOutputFile);
				for(LabeledDatum<Double, Integer> data : classifierTestingData)
				{
					//params.Dataset.getLabelString(l.intValue())
					Counter<Integer> prob = classifier.getProbabilities(data);
					for(Integer l : prob.keySet() )
		        		out.printf("%d : %.3f, ", l.intValue(), prob.getCount(l));
					out.println();
				}
				out.close();		
			}
			
			if (params.isTestLabelsKnown)
			{
				Accuracy TestAccuracy = classifier.test(classifierTestingData);
				System.out.println("Test Accuracy : " + TestAccuracy);
			}
		}
	}
	
	private FineTunableTheta train(Arguments params) throws IOException, ClassNotFoundException
	{
		InitialTheta = new FineTunableTheta(params.EmbeddingSize, params.EmbeddingSize, 
								params.CatSize, params.DictionarySize, true);

		FineTunableTheta tunedTheta = null;
		
		RAECost RAECost = new RAECost(params.AlphaCat,params.CatSize,params.Beta,params.DictionarySize,
				params.hiddenSize,params.visibleSize, params.Lambda,InitialTheta.We, params.Dataset.Data,null,f);
		Minimizer<DifferentiableFunction> minFunc = new QNMinimizer(10, params.MaxIterations);

		double[] minTheta = minFunc.minimize(RAECost, 1e-6, InitialTheta.Theta, params.MaxIterations);
		tunedTheta = new FineTunableTheta(minTheta, params.hiddenSize, 
										params.visibleSize, params.CatSize, params.DictionarySize);
	
		// Important step
		tunedTheta.setWe(tunedTheta.We.add(InitialTheta.We));
		return tunedTheta;
	}
	
	private FineTunableTheta load(Arguments params) throws IOException, ClassNotFoundException
	{
		FineTunableTheta tunedTheta = null;
		FileInputStream fis = new FileInputStream(params.ModelFile);
		ObjectInputStream ois = new ObjectInputStream(fis);
		tunedTheta = (FineTunableTheta) ois.readObject();
		ois.close();		
		return tunedTheta;
	}
}
